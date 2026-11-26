package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.PartyTrack
import com.example.model.SynthGroovePreset
import com.example.model.TrackSourceType
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

object AudioConstants {
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val FRAMES_PER_BLOCK = 512
}

/**
 * Real-time procedural stereo PCM synthesizer generating clean house-party grooves
 * and a precision acoustic sync calibration click pulse.
 */
class PartySynthEngine {
    private var sampleIndex: Long = 0L
    private var noiseState: Long = 0x5DEECE66DL

    fun reset() {
        sampleIndex = 0L
    }

    fun getPositionMs(bpm: Int): Long {
        return (sampleIndex * 1000L) / AudioConstants.SAMPLE_RATE
    }

    fun renderBlock(
        preset: SynthGroovePreset,
        outStereoPcm: ShortArray,
        frames: Int = AudioConstants.FRAMES_PER_BLOCK
    ) {
        val sr = AudioConstants.SAMPLE_RATE.toDouble()
        val beatsPerSecond = preset.bpm / 60.0
        val samplesPerBeat = sr / beatsPerSecond
        val samplesPer16th = samplesPerBeat / 4.0

        for (i in 0 until frames) {
            val s = sampleIndex++
            val beatPos = (s % (samplesPerBeat * 16.0))
            val beatInBar = (s % (samplesPerBeat * 4.0)) / samplesPerBeat
            val step16 = ((s / samplesPer16th).toInt()) % 16
            val stepTime = (s % samplesPer16th) / sr
            val beatTime = (s % samplesPerBeat) / sr

            var left = 0.0
            var right = 0.0

            when (preset) {
                SynthGroovePreset.NEON_PULSE -> {
                    // 4-on-the-floor punchy kick
                    val kickEnv = exp(-beatTime * 16.0)
                    val kickFreq = 52.0 + 110.0 * exp(-beatTime * 42.0)
                    val kick = sin(2.0 * PI * kickFreq * beatTime) * kickEnv * 0.62

                    // Off-beat crisp hi-hat
                    val hatGate = if (step16 % 4 == 2) 1.0 else if (step16 % 2 == 1) 0.35 else 0.0
                    val hat = nextWhiteNoise() * exp(-stepTime * 55.0) * 0.14 * hatGate

                    // Deep house bassline pattern (F# minor)
                    val bassNotes = doubleArrayOf(92.5, 0.0, 92.5, 110.0, 0.0, 123.47, 92.5, 0.0, 82.41, 0.0, 82.41, 110.0, 0.0, 138.59, 123.47, 110.0)
                    val bassFreq = bassNotes[step16]
                    val bassEnv = if (bassFreq > 0.0) exp(-stepTime * 8.5) else 0.0
                    val bassPhase = 2.0 * PI * bassFreq * (s / sr)
                    val bass = (sin(bassPhase) * 0.7 + sin(bassPhase * 2.0) * 0.25) * bassEnv * 0.34

                    // Stereo warm chord stab on beats 1.5 and 3.5
                    val barPhase = beatPos / samplesPerBeat
                    val chordGate = if (step16 == 3 || step16 == 10 || step16 == 14) 1.0 else 0.0
                    val chordEnv = exp(-stepTime * 7.0) * chordGate
                    val t = s / sr
                    val c1 = sin(2.0 * PI * 369.99 * t)
                    val c2 = sin(2.0 * PI * 440.00 * t)
                    val c3 = sin(2.0 * PI * 554.37 * t)
                    val c4 = sin(2.0 * PI * 659.25 * t)
                    val chordL = (c1 + c2 * 0.8 + c3 * 0.7) * chordEnv * 0.13
                    val chordR = (c1 * 0.8 + c3 + c4 * 0.75) * chordEnv * 0.13

                    left = kick + bass + hat * 0.9 + chordL
                    right = kick + bass + hat * 1.1 + chordR
                }

                SynthGroovePreset.MIDNIGHT_BASS -> {
                    val kickEnv = exp(-beatTime * 14.0)
                    val kickFreq = 46.0 + 135.0 * exp(-beatTime * 48.0)
                    val kick = sin(2.0 * PI * kickFreq * beatTime) * kickEnv * 0.68

                    // Clap on 2 and 4
                    val clapActive = (beatInBar >= 1.0 && beatInBar < 1.35) || (beatInBar >= 3.0 && beatInBar < 3.35)
                    val clap = if (clapActive) nextWhiteNoise() * exp(-beatTime * 22.0) * 0.24 else 0.0

                    // Driving rolling tech bass
                    val bassNotes = doubleArrayOf(55.0, 55.0, 65.41, 55.0, 55.0, 73.42, 65.41, 55.0, 49.0, 49.0, 55.0, 65.41, 73.42, 65.41, 55.0, 49.0)
                    val bf = bassNotes[step16]
                    val t = s / sr
                    val bEnv = exp(-stepTime * 11.0)
                    val bass = tanh((sin(2.0 * PI * bf * t) + 0.4 * sin(4.0 * PI * bf * t)) * 1.6) * bEnv * 0.32

                    // Lead arpeggio
                    val arpNotes = doubleArrayOf(440.0, 523.25, 659.25, 783.99, 659.25, 523.25, 440.0, 392.0)
                    val af = arpNotes[step16 % arpNotes.size]
                    val arpEnv = exp(-stepTime * 14.0)
                    val arp = sin(2.0 * PI * af * t) * arpEnv * 0.14
                    val pan = if (step16 % 2 == 0) 0.75 else 1.25

                    left = kick + bass + clap * 0.95 + arp * pan
                    right = kick + bass + clap * 1.05 + arp * (2.0 - pan)
                }

                SynthGroovePreset.SUNSET_LOUNGE -> {
                    val kickEnv = exp(-beatTime * 12.0)
                    val kickGate = if (step16 == 0 || step16 == 6 || step16 == 8 || step16 == 14) 1.0 else 0.0
                    val kick = sin(2.0 * PI * (50.0 + 80.0 * exp(-stepTime * 35.0)) * stepTime) * exp(-stepTime * 15.0) * kickGate * 0.48

                    val t = s / sr
                    val barIdx = ((s / (samplesPerBeat * 4.0)).toInt()) % 4
                    val root = when (barIdx) {
                        0 -> 261.63 // C4
                        1 -> 220.00 // A3
                        2 -> 174.61 // F3
                        else -> 196.00 // G3
                    }
                    val padEnv = 0.7 + 0.3 * sin(2.0 * PI * 0.5 * t)
                    val rhodesL = (sin(2.0 * PI * root * t) + 0.5 * sin(2.0 * PI * root * 1.25 * t) + 0.35 * sin(2.0 * PI * root * 1.5 * t)) * 0.16 * padEnv
                    val rhodesR = (sin(2.0 * PI * root * t) + 0.45 * sin(2.0 * PI * root * 1.25 * t + 0.2) + 0.4 * sin(2.0 * PI * root * 1.875 * t)) * 0.16 * padEnv
                    val shaker = nextWhiteNoise() * exp(-stepTime * 65.0) * 0.07

                    left = kick + rhodesL + shaker * 0.8
                    right = kick + rhodesR + shaker * 1.2
                }

                SynthGroovePreset.ELECTRIC_EUPHORIA -> {
                    val t = s / sr
                    // Punchy gated synthwave kick + snare on 2 and 4
                    val kick = sin(2.0 * PI * (54.0 + 120.0 * exp(-beatTime * 40.0)) * beatTime) * exp(-beatTime * 15.0) * 0.56
                    val isSnare = (beatInBar >= 1.0 && beatInBar < 1.28) || (beatInBar >= 3.0 && beatInBar < 3.28)
                    val snare = if (isSnare) nextWhiteNoise() * exp(-beatTime * 18.0) * 0.22 else 0.0

                    // Driving 16th-note octave synth bass
                    val arpBass = doubleArrayOf(110.0, 220.0, 110.0, 220.0, 130.81, 261.63, 130.81, 261.63, 98.0, 196.0, 98.0, 196.0, 146.83, 293.66, 130.81, 196.0)
                    val bf = arpBass[step16]
                    val bassEnv = exp(-stepTime * 9.5)
                    val bass = tanh(sin(2.0 * PI * bf * t) * 1.4) * bassEnv * 0.30

                    // Shimmering wide synthwave lead
                    val leadNotes = doubleArrayOf(440.0, 523.25, 659.25, 587.33, 523.25, 659.25, 783.99, 659.25)
                    val lf = leadNotes[step16 % leadNotes.size]
                    val leadL = sin(2.0 * PI * lf * t) * exp(-stepTime * 8.0) * 0.16
                    val leadR = sin(2.0 * PI * (lf * 1.004) * t) * exp(-stepTime * 8.0) * 0.16

                    left = kick + snare + bass + leadL
                    right = kick + snare + bass + leadR
                }

                SynthGroovePreset.ROOFTOP_GROOVE -> {
                    val t = s / sr
                    // Syncopated afrobeat kick & rim percussion
                    val kickGate = if (step16 == 0 || step16 == 3 || step16 == 6 || step16 == 8 || step16 == 11 || step16 == 14) 1.0 else 0.0
                    val kick = sin(2.0 * PI * (56.0 + 95.0 * exp(-stepTime * 36.0)) * stepTime) * exp(-stepTime * 14.0) * kickGate * 0.52
                    val rimGate = if (step16 == 2 || step16 == 5 || step16 == 10 || step16 == 13) 1.0 else 0.0
                    val rim = sin(2.0 * PI * 880.0 * stepTime) * exp(-stepTime * 48.0) * rimGate * 0.20

                    // Warm marimba-like chord bounce
                    val notes = doubleArrayOf(293.66, 349.23, 440.0, 523.25, 440.0, 349.23, 392.0, 523.25)
                    val mf = notes[step16 % notes.size]
                    val marimba = (sin(2.0 * PI * mf * t) + 0.35 * sin(2.0 * PI * mf * 2.0 * t)) * exp(-stepTime * 12.0) * 0.24
                    val subBass = sin(2.0 * PI * (mf * 0.25) * t) * exp(-beatTime * 6.0) * 0.26

                    left = kick + rim * 0.8 + marimba * 1.1 + subBass
                    right = kick + rim * 1.2 + marimba * 0.9 + subBass
                }

                SynthGroovePreset.CALIBRATION_CLICK -> {
                    // Sharp 1kHz / 2kHz dual transient impulse every beat (500ms at 120 BPM)
                    val clickEnv = exp(-beatTime * 90.0)
                    val tone = sin(2.0 * PI * 1200.0 * beatTime) + 0.5 * sin(2.0 * PI * 2400.0 * beatTime)
                    val sample = tone * clickEnv * 0.65
                    left = sample
                    right = sample
                }
            }

            // Soft-limit to prevent clipping across multi-layer synthesis
            val outL = (tanh(left) * 30000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            val outR = (tanh(right) * 30000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            outStereoPcm[i * 2] = outL
            outStereoPcm[i * 2 + 1] = outR
        }
    }

    private fun nextWhiteNoise(): Double {
        noiseState = (noiseState * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        val bits = (noiseState ushr 16).toInt()
        return (bits / 2147483648.0)
    }
}

enum class DecodeBlockResult {
    OK,
    END_OF_TRACK,
    ERROR
}

/**
 * Decodes user-selected local audio files (MP3, AAC, WAV, FLAC, OGG) into 44.1kHz stereo PCM
 * using Android's MediaExtractor + MediaCodec with automatic resampling, metadata lookup, & queue completion.
 */
class LocalAudioDecoder(private val context: Context) {
    private val decoderLock = Any()
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var inputDone = false
    private var outputDone = false
    private var sourceSampleRate = AudioConstants.SAMPLE_RATE
    private var sourceChannels = 2
    private var durationUs = 0L
    private var currentPositionUs = 0L
    private var pendingShorts = ShortArray(0)
    private var pendingOffset = 0

    fun extractTrackMetadata(uri: Uri): PartyTrack? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

                val fallbackName = (queryDisplayName(uri) ?: uri.lastPathSegment ?: "Local Audio Track")
                    .substringBeforeLast('.')
                val title = rawTitle?.takeIf { it.isNotBlank() } ?: fallbackName
                val artist = rawArtist?.takeIf { it.isNotBlank() } ?: "Local Audio"
                val album = rawAlbum?.takeIf { it.isNotBlank() } ?: "Device Library"

                PartyTrack(
                    id = "local_${uri.toString().hashCode()}",
                    title = title,
                    artist = artist,
                    album = album,
                    bpm = 120,
                    durationMs = durationMs,
                    sourceType = TrackSourceType.LOCAL_FILE,
                    uriString = uri.toString()
                )
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }

    fun loadAlbumArtBytes(uri: Uri): ByteArray? {
        val embedded = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (embedded != null) return embedded

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return runCatching {
                val bmp = context.contentResolver.loadThumbnail(uri, android.util.Size(320, 320), null)
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                bmp.recycle()
                out.toByteArray()
            }.getOrNull()
        }
        return null
    }

    fun load(uri: Uri): PartyTrack? {
        val meta = extractTrackMetadata(uri)
        synchronized(decoderLock) {
            releaseLocked()
            return runCatching {
                val ext = MediaExtractor()
                ext.setDataSource(context, uri, null)
                var audioTrackIndex = -1
                var trackFormat: MediaFormat? = null
                for (i in 0 until ext.trackCount) {
                    val fmt = ext.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        trackFormat = fmt
                        break
                    }
                }
                if (audioTrackIndex < 0 || trackFormat == null) {
                    ext.release()
                    return null
                }

                ext.selectTrack(audioTrackIndex)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
                sourceSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                sourceChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    trackFormat.getLong(MediaFormat.KEY_DURATION)
                } else {
                    (meta?.durationMs ?: 0L) * 1000L
                }

                val mediaCodec = MediaCodec.createDecoderByType(mime)
                mediaCodec.configure(trackFormat, null, null, 0)
                mediaCodec.start()

                extractor = ext
                codec = mediaCodec
                inputDone = false
                outputDone = false
                pendingShorts = ShortArray(0)
                pendingOffset = 0
                currentPositionUs = 0L

                val fallbackName = (queryDisplayName(uri) ?: uri.lastPathSegment ?: "Local Audio Track")
                    .substringBeforeLast('.')
                PartyTrack(
                    id = "local_${uri.toString().hashCode()}",
                    title = meta?.title ?: fallbackName,
                    artist = meta?.artist ?: "Local Audio • ${sourceSampleRate / 1000}kHz",
                    album = meta?.album ?: "Device Library",
                    bpm = 120,
                    durationMs = (durationUs / 1000L).coerceAtLeast(meta?.durationMs ?: 0L),
                    sourceType = TrackSourceType.LOCAL_FILE,
                    uriString = uri.toString()
                )
            }.getOrNull()
        }
    }

    fun getPositionMs(): Long = synchronized(decoderLock) { currentPositionUs / 1000L }

    fun seekToMs(positionMs: Long) {
        synchronized(decoderLock) {
            seekLocked(positionMs)
        }
    }

    private fun seekLocked(positionMs: Long) {
        val ext = extractor ?: return
        val mc = codec ?: return
        val targetUs = (positionMs * 1000L).coerceAtLeast(0L)
        runCatching {
            ext.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            mc.flush()
            inputDone = false
            outputDone = false
            pendingShorts = ShortArray(0)
            pendingOffset = 0
            currentPositionUs = targetUs
        }
    }

    fun readStereoBlock(
        outStereoPcm: ShortArray,
        frames: Int = AudioConstants.FRAMES_PER_BLOCK,
        loopOnEnd: Boolean = false
    ): DecodeBlockResult {
        synchronized(decoderLock) {
            val mc = codec ?: return DecodeBlockResult.ERROR
            val ext = extractor ?: return DecodeBlockResult.ERROR
            val neededShorts = frames * 2
            var written = 0

            while (written < neededShorts) {
                if (pendingOffset < pendingShorts.size) {
                    val toCopy = minOf(neededShorts - written, pendingShorts.size - pendingOffset)
                    System.arraycopy(pendingShorts, pendingOffset, outStereoPcm, written, toCopy)
                    pendingOffset += toCopy
                    written += toCopy
                    continue
                }

                if (outputDone) {
                    if (loopOnEnd) {
                        seekLocked(0L)
                        if (outputDone) break
                    } else {
                        if (written < neededShorts) {
                            outStereoPcm.fill(0, written, neededShorts)
                        }
                        return DecodeBlockResult.END_OF_TRACK
                    }
                }

                if (!inputDone) {
                    val inIdx = mc.dequeueInputBuffer(2000L)
                    if (inIdx >= 0) {
                        val buf = mc.getInputBuffer(inIdx)
                        if (buf != null) {
                            val sampleSize = ext.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                mc.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = ext.sampleTime
                                mc.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                ext.advance()
                            }
                        }
                    }
                }

                val info = MediaCodec.BufferInfo()
                val outIdx = mc.dequeueOutputBuffer(info, 2000L)
                when {
                    outIdx >= 0 -> {
                        val outBuf = mc.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            val shortBuf = outBuf.asShortBuffer()
                            val raw = ShortArray(shortBuf.remaining())
                            shortBuf.get(raw)
                            currentPositionUs = info.presentationTimeUs
                            pendingShorts = normalizeTo44kStereo(raw, sourceChannels, sourceSampleRate)
                            pendingOffset = 0
                        }
                        mc.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val fmt = mc.outputFormat
                        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sourceSampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            sourceChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone && pendingOffset >= pendingShorts.size) {
                            outputDone = true
                        }
                    }
                }
            }

            if (written < neededShorts) {
                outStereoPcm.fill(0, written, neededShorts)
            }
            return DecodeBlockResult.OK
        }
    }

    private fun normalizeTo44kStereo(raw: ShortArray, channels: Int, sampleRate: Int): ShortArray {
        val stereo = if (channels == 1) {
            ShortArray(raw.size * 2).also { dst ->
                for (i in raw.indices) {
                    dst[i * 2] = raw[i]
                    dst[i * 2 + 1] = raw[i]
                }
            }
        } else if (channels > 2) {
            val frames = raw.size / channels
            ShortArray(frames * 2).also { dst ->
                for (f in 0 until frames) {
                    dst[f * 2] = raw[f * channels]
                    dst[f * 2 + 1] = raw[f * channels + 1]
                }
            }
        } else {
            raw
        }

        if (sampleRate == AudioConstants.SAMPLE_RATE || sampleRate <= 0) {
            return stereo
        }

        val inFrames = stereo.size / 2
        if (inFrames == 0) return stereo
        val ratio = sampleRate.toDouble() / AudioConstants.SAMPLE_RATE.toDouble()
        val outFrames = ((inFrames / ratio).toInt()).coerceAtLeast(1)
        val resampled = ShortArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, inFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, inFrames - 1)
            val frac = srcPos - idx0
            val l = stereo[idx0 * 2] * (1.0 - frac) + stereo[idx1 * 2] * frac
            val r = stereo[idx0 * 2 + 1] * (1.0 - frac) + stereo[idx1 * 2 + 1] * frac
            resampled[i * 2] = l.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            resampled[i * 2 + 1] = r.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return resampled
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    fun release() {
        synchronized(decoderLock) {
            releaseLocked()
        }
    }

    private fun releaseLocked() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        codec = null
        extractor = null
        pendingShorts = ShortArray(0)
        pendingOffset = 0
    }
}
