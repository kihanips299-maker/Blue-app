package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.model.EngineDiagnostics
import com.example.model.EqualizerPreset
import com.example.model.PartyTrack
import com.example.model.SynthGroovePreset
import com.example.model.TrackSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

data class SinkRoutingTarget(
    val stableId: String,
    val audioDeviceInfo: AudioDeviceInfo,
    val volume: Float,
    val isMuted: Boolean,
    val latencyOffsetMs: Int
)

private class ActiveSinkTrack(
    val stableId: String,
    val deviceId: Int,
    val track: AudioTrack,
    @Volatile var gain: Float,
    @Volatile var latencyOffsetMs: Int
)

class MultiSinkAudioEngine(
    private val context: Context,
    private val onNoisyDisconnect: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val synthEngine = PartySynthEngine()
    private val localDecoder = LocalAudioDecoder(context)
    private val eqProcessor = EqualizerDspProcessor()

    private val isPlayingFlag = AtomicBoolean(false)
    private var audioThread: Thread? = null

    @Volatile
    private var lowLatencyMode: Boolean = true

    @Volatile
    private var masterVolume: Float = 0.85f

    @Volatile
    private var isMasterMuted: Boolean = false

    @Volatile
    private var activeTrack: PartyTrack = PartyTrack(
        id = SynthGroovePreset.NEON_PULSE.id,
        title = SynthGroovePreset.NEON_PULSE.title,
        artist = SynthGroovePreset.NEON_PULSE.subtitle,
        bpm = SynthGroovePreset.NEON_PULSE.bpm,
        durationMs = 0L,
        sourceType = TrackSourceType.SYNTH_GROOVE,
        synthPreset = SynthGroovePreset.NEON_PULSE
    )

    @Volatile
    private var currentTargets: List<SinkRoutingTarget> = emptyList()

    private val activeSinks = ConcurrentHashMap<String, ActiveSinkTrack>()
    private val sinkLock = Any()

    // 1.0 second circular stereo PCM buffer (44,100 frames * 2 channels)
    private val ringBufferFrames = AudioConstants.SAMPLE_RATE
    private val ringBufferShorts = ShortArray(ringBufferFrames * AudioConstants.CHANNELS)
    private var masterWriteFrame: Long = 0L
    private val baseHeadroomMs = 180

    private val _diagnostics = MutableStateFlow(EngineDiagnostics())
    val diagnostics: StateFlow<EngineDiagnostics> = _diagnostics.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _sinkRmsMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sinkRmsMap: StateFlow<Map<String, Float>> = _sinkRmsMap.asStateFlow()

    private val _sinkDriftMsMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sinkDriftMsMap: StateFlow<Map<String, Float>> = _sinkDriftMsMap.asStateFlow()

    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onNoisyDisconnect()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                applyVolumesToAllSinks(duckFactor = 0.35f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                applyVolumesToAllSinks(duckFactor = 1.0f)
            }
        }
    }

    fun updateMasterVolume(volume: Float, muted: Boolean) {
        masterVolume = volume.coerceIn(0f, 1f)
        isMasterMuted = muted
        applyVolumesToAllSinks()
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        eqProcessor.setPreset(preset)
    }

    fun setLowLatencyMode(enabled: Boolean) {
        if (lowLatencyMode == enabled) return
        lowLatencyMode = enabled
        if (isPlayingFlag.get()) {
            synchronized(sinkLock) {
                rebuildSinksLocked(currentTargets)
            }
        }
        _diagnostics.value = _diagnostics.value.copy(
            performanceModeLabel = if (enabled) "LOW_LATENCY" else "STANDARD"
        )
    }

    fun selectSynthTrack(preset: SynthGroovePreset): PartyTrack {
        val track = PartyTrack(
            id = preset.id,
            title = preset.title,
            artist = preset.subtitle,
            bpm = preset.bpm,
            durationMs = 0L,
            sourceType = TrackSourceType.SYNTH_GROOVE,
            synthPreset = preset
        )
        activeTrack = track
        synthEngine.reset()
        _positionMs.value = 0L
        return track
    }

    fun loadLocalTrack(uri: Uri): PartyTrack? {
        val decoded = localDecoder.load(uri) ?: return null
        activeTrack = decoded
        _positionMs.value = 0L
        return decoded
    }

    fun seekToMs(ms: Long) {
        if (activeTrack.sourceType == TrackSourceType.LOCAL_FILE) {
            localDecoder.seekToMs(ms)
            _positionMs.value = ms
        }
    }

    fun updateTargets(targets: List<SinkRoutingTarget>) {
        currentTargets = targets
        synchronized(sinkLock) {
            val targetIds = targets.map { it.stableId }.toSet()
            val toRemove = activeSinks.keys.filter { it !in targetIds }
            toRemove.forEach { key ->
                activeSinks.remove(key)?.let { releaseAudioTrack(it.track) }
            }

            targets.forEach { target ->
                val existing = activeSinks[target.stableId]
                val effectiveGain = computePerceptualGain(target.volume, target.isMuted)
                if (existing != null && existing.deviceId == target.audioDeviceInfo.id) {
                    existing.gain = effectiveGain
                    existing.latencyOffsetMs = target.latencyOffsetMs
                    runCatching { existing.track.setVolume(effectiveGain) }
                } else {
                    existing?.let { releaseAudioTrack(it.track) }
                    if (isPlayingFlag.get()) {
                        buildAudioTrack(target)?.let { created ->
                            activeSinks[target.stableId] = created
                        }
                    }
                }
            }
        }
        _diagnostics.value = _diagnostics.value.copy(activeSinkCount = targets.size)
    }

    fun play() {
        if (isPlayingFlag.get()) return
        val focusGranted = requestFocus()
        registerNoisyReceiver()
        isPlayingFlag.set(true)

        synchronized(sinkLock) {
            masterWriteFrame = 0L
            ringBufferShorts.fill(0)
            eqProcessor.resetStates()
            rebuildSinksLocked(currentTargets)
        }

        _diagnostics.value = _diagnostics.value.copy(
            hasAudioFocus = focusGranted,
            performanceModeLabel = if (lowLatencyMode) "LOW_LATENCY" else "STANDARD",
            activeSinkCount = activeSinks.size
        )

        audioThread = Thread({ runAudioLoop() }, "AmpSyncAudioEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun pause() {
        if (!isPlayingFlag.getAndSet(false)) return
        runCatching { audioThread?.join(400L) }
        audioThread = null
        synchronized(sinkLock) {
            activeSinks.values.forEach { releaseAudioTrack(it.track) }
            activeSinks.clear()
        }
        unregisterNoisyReceiver()
        abandonFocus()
        _sinkRmsMap.value = emptyMap()
        _diagnostics.value = _diagnostics.value.copy(
            hasAudioFocus = false,
            masterRms = 0f,
            spectrumBands = List(16) { 0f }
        )
    }

    fun release() {
        pause()
        localDecoder.release()
    }

    private fun runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val blockFrames = AudioConstants.FRAMES_PER_BLOCK
        val sourcePcm = ShortArray(blockFrames * AudioConstants.CHANNELS)
        val sinkScratch = ShortArray(blockFrames * AudioConstants.CHANNELS)
        val baseHeadroomFrames = (AudioConstants.SAMPLE_RATE * baseHeadroomMs) / 1000L
        var uiTickCounter = 0

        while (isPlayingFlag.get()) {
            val track = activeTrack
            when (track.sourceType) {
                TrackSourceType.SYNTH_GROOVE -> {
                    val preset = track.synthPreset ?: SynthGroovePreset.NEON_PULSE
                    synthEngine.renderBlock(preset, sourcePcm, blockFrames)
                }
                TrackSourceType.LOCAL_FILE -> {
                    val ok = localDecoder.readStereoBlock(sourcePcm, blockFrames)
                    if (!ok) {
                        synthEngine.renderBlock(SynthGroovePreset.NEON_PULSE, sourcePcm, blockFrames)
                    }
                }
            }

            // Apply active 5-band Equalizer DSP to stereo PCM before multi-speaker ring-buffer distribution
            eqProcessor.processStereoPcm(sourcePcm, blockFrames)

            // Write newly generated block into circular ring buffer
            for (f in 0 until blockFrames) {
                val ringIdx = ((masterWriteFrame + f) % ringBufferFrames).toInt() * 2
                ringBufferShorts[ringIdx] = sourcePcm[f * 2]
                ringBufferShorts[ringIdx + 1] = sourcePcm[f * 2 + 1]
            }

            val sinksSnapshot = activeSinks.values.toList()
            val currentWriteStart = masterWriteFrame
            masterWriteFrame += blockFrames

            if (sinksSnapshot.isEmpty()) {
                // Keep cadence steady if no sink is currently enabled
                Thread.sleep((blockFrames * 1000L) / AudioConstants.SAMPLE_RATE)
            } else {
                // Feed each AudioTrack from its phase-aligned cursor
                for (sink in sinksSnapshot) {
                    val offsetFrames = (sink.latencyOffsetMs.toLong() * AudioConstants.SAMPLE_RATE) / 1000L
                    val readStartFrame = currentWriteStart - baseHeadroomFrames - offsetFrames
                    if (readStartFrame < 0L) {
                        sinkScratch.fill(0)
                    } else {
                        for (f in 0 until blockFrames) {
                            val srcIdx = ((readStartFrame + f) % ringBufferFrames).toInt() * 2
                            sinkScratch[f * 2] = ringBufferShorts[srcIdx]
                            sinkScratch[f * 2 + 1] = ringBufferShorts[srcIdx + 1]
                        }
                    }
                    runCatching {
                        sink.track.write(sinkScratch, 0, sinkScratch.size, AudioTrack.WRITE_BLOCKING)
                    }
                }
            }

            uiTickCounter++
            if (uiTickCounter % 2 == 0) {
                updateTelemetry(sourcePcm, sinksSnapshot, track)
            }
        }
    }

    private fun updateTelemetry(
        sourcePcm: ShortArray,
        sinks: List<ActiveSinkTrack>,
        track: PartyTrack
    ) {
        var sumSq = 0.0
        for (s in sourcePcm) {
            val norm = s / 32768.0
            sumSq += norm * norm
        }
        val rawRms = sqrt(sumSq / sourcePcm.size).toFloat().coerceIn(0f, 1f)
        val masterScaledRms = (rawRms * if (isMasterMuted) 0f else masterVolume).coerceIn(0f, 1f)

        // Compute 16 visual energy bands from sub-windows of the PCM block
        val bandSize = (sourcePcm.size / 16).coerceAtLeast(2)
        val bands = List(16) { b ->
            val start = b * bandSize
            val end = minOf(start + bandSize, sourcePcm.size)
            var peak = 0f
            for (i in start until end step 2) {
                val v = abs(sourcePcm[i].toInt()) / 32768f
                if (v > peak) peak = v
            }
            (peak * if (isMasterMuted) 0f else (0.35f + 0.65f * masterVolume)).coerceIn(0.04f, 1f)
        }

        val rmsByDevice = mutableMapOf<String, Float>()
        val driftByDevice = mutableMapOf<String, Float>()
        val timestamp = AudioTimestamp()
        val headPositions = mutableListOf<Pair<String, Long>>()

        sinks.forEach { sink ->
            rmsByDevice[sink.stableId] = (rawRms * sink.gain).coerceIn(0f, 1f)
            val hasTs = runCatching { sink.track.getTimestamp(timestamp) }.getOrDefault(false)
            val headPos = if (hasTs) {
                timestamp.framePosition
            } else {
                runCatching { sink.track.playbackHeadPosition.toLong() }.getOrDefault(0L)
            }
            headPositions.add(sink.stableId to headPos)
        }

        var maxDriftMs = 0f
        if (headPositions.size >= 2) {
            val avgHead = headPositions.map { it.second }.average()
            headPositions.forEach { (id, pos) ->
                val driftMs = ((pos - avgHead) * 1000.0 / AudioConstants.SAMPLE_RATE).toFloat()
                driftByDevice[id] = driftMs
                maxDriftMs = maxOf(maxDriftMs, abs(driftMs))
            }
        } else {
            headPositions.forEach { (id, _) -> driftByDevice[id] = 0f }
        }

        _sinkRmsMap.value = rmsByDevice
        _sinkDriftMsMap.value = driftByDevice
        _positionMs.value = when (track.sourceType) {
            TrackSourceType.SYNTH_GROOVE -> synthEngine.getPositionMs(track.bpm)
            TrackSourceType.LOCAL_FILE -> localDecoder.getPositionMs()
        }
        _diagnostics.value = _diagnostics.value.copy(
            sampleRateHz = AudioConstants.SAMPLE_RATE,
            bufferFrames = AudioConstants.FRAMES_PER_BLOCK,
            performanceModeLabel = if (lowLatencyMode) "LOW_LATENCY" else "STANDARD",
            activeSinkCount = sinks.size,
            maxSyncDriftMs = maxDriftMs,
            masterRms = masterScaledRms,
            spectrumBands = bands
        )
    }

    private fun rebuildSinksLocked(targets: List<SinkRoutingTarget>) {
        activeSinks.values.forEach { releaseAudioTrack(it.track) }
        activeSinks.clear()
        targets.forEach { target ->
            buildAudioTrack(target)?.let { created ->
                activeSinks[target.stableId] = created
            }
        }
    }

    private fun buildAudioTrack(target: SinkRoutingTarget): ActiveSinkTrack? {
        return runCatching {
            val minBufBytes = AudioTrack.getMinBufferSize(
                AudioConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(AudioConstants.FRAMES_PER_BLOCK * 4 * 2)

            val bufferBytes = if (lowLatencyMode) minBufBytes else minBufBytes * 2

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(AudioConstants.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val builder = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(
                    if (lowLatencyMode) AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                    else AudioTrack.PERFORMANCE_MODE_NONE
                )
            }

            val track = builder.build()
            track.setPreferredDevice(target.audioDeviceInfo)
            val gain = computePerceptualGain(target.volume, target.isMuted)
            track.setVolume(gain)
            track.play()

            ActiveSinkTrack(
                stableId = target.stableId,
                deviceId = target.audioDeviceInfo.id,
                track = track,
                gain = gain,
                latencyOffsetMs = target.latencyOffsetMs
            )
        }.getOrNull()
    }

    private fun releaseAudioTrack(track: AudioTrack) {
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
            track.release()
        }
    }

    private fun applyVolumesToAllSinks(duckFactor: Float = 1.0f) {
        currentTargets.forEach { target ->
            val sink = activeSinks[target.stableId] ?: return@forEach
            val gain = computePerceptualGain(target.volume, target.isMuted) * duckFactor
            sink.gain = gain
            runCatching { sink.track.setVolume(gain) }
        }
    }

    private fun computePerceptualGain(speakerVolume: Float, isSpeakerMuted: Boolean): Float {
        val linear = computeProportionalLinearLevel(
            masterVolume = masterVolume,
            speakerVolume = speakerVolume,
            isMasterMuted = isMasterMuted,
            isSpeakerMuted = isSpeakerMuted
        )
        // Smooth quadratic taper feels natural on mixer faders
        return linear * linear
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { context.unregisterReceiver(becomingNoisyReceiver) }
        noisyReceiverRegistered = false
    }

    companion object {
        fun computeProportionalLinearLevel(
            masterVolume: Float,
            speakerVolume: Float,
            isMasterMuted: Boolean,
            isSpeakerMuted: Boolean
        ): Float {
            if (isMasterMuted || isSpeakerMuted) return 0f
            return (masterVolume.coerceIn(0f, 1f) * speakerVolume.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }
    }
}
