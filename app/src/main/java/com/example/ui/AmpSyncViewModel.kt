package com.example.ui

import android.app.Application
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.MultiSinkAudioEngine
import com.example.audio.PartyAudioService
import com.example.audio.SinkRoutingTarget
import com.example.bluetooth.BluetoothAudioMonitor
import com.example.data.PartyPreferencesRepository
import com.example.data.PersistedSettings
import com.example.model.AudioOutputDevice
import com.example.model.EqualizerPreset
import com.example.model.MusicLibraryFilter
import com.example.model.PartyPreset
import com.example.model.PartyTrack
import com.example.model.PartyUiState
import com.example.model.RepeatMode
import com.example.model.RoomZone
import com.example.model.SavedDeviceConfig
import com.example.model.SpeakerTransportType
import com.example.model.SynthGroovePreset
import com.example.model.TrackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class AmpSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefsRepo = PartyPreferencesRepository(appContext)
    private val bluetoothMonitor = BluetoothAudioMonitor(appContext)

    private var previousTrackBeforeCalibration: PartyTrack? = null

    private val audioEngine = MultiSinkAudioEngine(
        context = appContext,
        onNoisyDisconnect = {
            if (_uiState.value.autoPauseOnDisconnect && _uiState.value.isPlaying) {
                pausePlayback(banner = "Audio output disconnected — paused to prevent sudden noise.")
            }
        },
        onTrackCompleted = {
            viewModelScope.launch(Dispatchers.Main) {
                handleTrackCompleted()
            }
        }
    )

    private val builtInPlaylist: List<PartyTrack> = listOf(
        SynthGroovePreset.NEON_PULSE,
        SynthGroovePreset.MIDNIGHT_BASS,
        SynthGroovePreset.SUNSET_LOUNGE,
        SynthGroovePreset.ELECTRIC_EUPHORIA,
        SynthGroovePreset.ROOFTOP_GROOVE
    ).map { preset ->
        PartyTrack(
            id = preset.id,
            title = preset.title,
            artist = preset.subtitle,
            album = "AmpSync Synth Library",
            bpm = preset.bpm,
            durationMs = 0L,
            sourceType = TrackSourceType.SYNTH_GROOVE,
            synthPreset = preset
        )
    }

    private val _uiState = MutableStateFlow(
        PartyUiState(playlist = builtInPlaylist, currentTrack = builtInPlaylist.first())
    )
    val uiState: StateFlow<PartyUiState> = _uiState.asStateFlow()

    private val _currentAlbumArt = MutableStateFlow<ByteArray?>(null)
    val currentAlbumArt: StateFlow<ByteArray?> = _currentAlbumArt.asStateFlow()

    private val soloDeviceId = MutableStateFlow<String?>(null)
    private val latestAudioDeviceMap = MutableStateFlow<Map<String, AudioDeviceInfo>>(emptyMap())
    private var latestSettings = PersistedSettings()

    init {
        bluetoothMonitor.start()
        observeSystemAndSettings()
        observeEngineTelemetry()
    }

    private fun observeSystemAndSettings() {
        viewModelScope.launch {
            combine(
                bluetoothMonitor.systemOutputDevices,
                bluetoothMonitor.pairedSpeakers,
                bluetoothMonitor.discoveredDevices,
                bluetoothMonitor.isScanning,
                bluetoothMonitor.capabilities
            ) { sysDevices, pairedBt, discovered, scanning, caps ->
                SystemSnapshot(sysDevices, pairedBt, discovered, scanning, caps)
            }.combine(prefsRepo.settingsFlow) { sys, settings ->
                Pair(sys, settings)
            }.combine(soloDeviceId) { (sys, settings), soloId ->
                Triple(sys, settings, soloId)
            }.collect { (sys, settings, soloId) ->
                latestSettings = settings
                audioEngine.updateMasterVolume(settings.masterVolume, _uiState.value.isMasterMuted)
                audioEngine.setLowLatencyMode(settings.lowLatencyMode)
                audioEngine.setEqualizerPreset(settings.equalizerPreset)
                audioEngine.setRepeatOne(settings.repeatMode == RepeatMode.REPEAT_ONE)

                val infoMap = mutableMapOf<String, AudioDeviceInfo>()
                val devices = mutableListOf<AudioOutputDevice>()
                val seenIds = mutableSetOf<String>()
                var accentCounter = 0

                val connectedSystemSinks = sys.sysDevices.sortedBy { info ->
                    val transport = BluetoothAudioMonitor.mapTransportType(info)
                    when (transport) {
                        SpeakerTransportType.BLUETOOTH_LE_BROADCAST -> 0
                        SpeakerTransportType.BLUETOOTH_LE_AUDIO -> 1
                        SpeakerTransportType.BLUETOOTH_A2DP -> 2
                        SpeakerTransportType.USB_AUDIO -> 3
                        SpeakerTransportType.WIRED_AUDIO -> 4
                        SpeakerTransportType.BUILT_IN_SPEAKER -> 5
                    }
                }

                connectedSystemSinks.forEach { info ->
                    val stableId = BluetoothAudioMonitor.buildStableDeviceId(info)
                    if (!seenIds.add(stableId)) return@forEach
                    infoMap[stableId] = info

                    val transport = BluetoothAudioMonitor.mapTransportType(info)
                    val saved = settings.deviceConfigs[stableId]
                    val defaultZone = when (accentCounter % 4) {
                        0 -> RoomZone.LIVING_ROOM
                        1 -> RoomZone.KITCHEN
                        2 -> RoomZone.PATIO
                        else -> RoomZone.DINING
                    }
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        info.address.takeIf { !it.isNullOrBlank() }
                    } else null

                    val rawName = info.productName?.toString()?.trim().orEmpty()
                    val displayName = when {
                        transport == SpeakerTransportType.BUILT_IN_SPEAKER &&
                            (rawName.isBlank() || rawName.equals("sdk_gphone64_x86_64", true) || rawName.equals("Android", true)) ->
                            "Device Speaker"
                        rawName.isNotBlank() -> rawName
                        else -> transport.label
                    }

                    val enabled = saved?.isEnabledInParty ?: true
                    val isMutedBySolo = soloId != null && soloId != stableId
                    val effectiveMuted = (saved?.isMuted ?: false) || isMutedBySolo
                    val matchedBt = sys.pairedBt.find { bt ->
                        (address != null && bt.address.equals(address, ignoreCase = true)) ||
                            bt.name.equals(displayName, ignoreCase = true)
                    }

                    devices.add(
                        AudioOutputDevice(
                            id = stableId,
                            systemDeviceId = info.id,
                            name = displayName,
                            address = address,
                            transportType = transport,
                            isConnected = true,
                            isPaired = transport == SpeakerTransportType.BLUETOOTH_A2DP ||
                                transport == SpeakerTransportType.BLUETOOTH_LE_AUDIO ||
                                transport == SpeakerTransportType.BLUETOOTH_LE_BROADCAST,
                            isEnabledInParty = enabled,
                            volume = saved?.volume ?: 0.85f,
                            isMuted = effectiveMuted,
                            isSolo = soloId == stableId,
                            latencyOffsetMs = saved?.latencyOffsetMs ?: 0,
                            roomZone = saved?.roomZone ?: defaultZone,
                            accentIndex = accentCounter++,
                            supportedSampleRates = info.sampleRates.toList(),
                            batteryPercent = matchedBt?.batteryPercent
                        )
                    )
                }

                // Append paired but currently disconnected Bluetooth speakers
                sys.pairedBt.forEach { bt ->
                    val stableId = "bt_${bt.address}"
                    if (seenIds.contains(stableId)) return@forEach
                    val alreadyMatchedByName = devices.any {
                        it.isConnected && it.name.equals(bt.name, ignoreCase = true)
                    }
                    if (alreadyMatchedByName) return@forEach
                    seenIds.add(stableId)

                    val saved = settings.deviceConfigs[stableId]
                    devices.add(
                        AudioOutputDevice(
                            id = stableId,
                            systemDeviceId = null,
                            name = bt.name,
                            address = bt.address,
                            transportType = if (bt.isLeAudio) SpeakerTransportType.BLUETOOTH_LE_AUDIO else SpeakerTransportType.BLUETOOTH_A2DP,
                            isConnected = false,
                            isPaired = true,
                            isEnabledInParty = saved?.isEnabledInParty ?: false,
                            volume = saved?.volume ?: 0.80f,
                            isMuted = saved?.isMuted ?: false,
                            isSolo = false,
                            latencyOffsetMs = saved?.latencyOffsetMs ?: 0,
                            roomZone = saved?.roomZone ?: RoomZone.PATIO,
                            accentIndex = accentCounter++
                        )
                    )
                }

                latestAudioDeviceMap.value = infoMap
                pushRoutingTargetsToEngine(devices, infoMap)

                _uiState.update { current ->
                    val currentRms = audioEngine.sinkRmsMap.value
                    val currentDrift = audioEngine.sinkDriftMsMap.value
                    val enrichedDevices = devices.map { dev ->
                        dev.copy(
                            rmsLevel = currentRms[dev.id] ?: 0f,
                            measuredDriftMs = currentDrift[dev.id] ?: 0f
                        )
                    }
                    val combinedPlaylist = settings.importedTracks + builtInPlaylist
                    current.copy(
                        masterVolume = settings.masterVolume,
                        lowLatencyMode = settings.lowLatencyMode,
                        autoPauseOnDisconnect = settings.autoPauseOnDisconnect,
                        equalizerPreset = settings.equalizerPreset,
                        isShuffleEnabled = settings.isShuffleEnabled,
                        repeatMode = settings.repeatMode,
                        playlist = combinedPlaylist,
                        outputDevices = enrichedDevices,
                        discoveredDevices = sys.discovered,
                        isScanning = sys.scanning,
                        bluetoothCapabilities = sys.caps,
                        presets = settings.presets,
                        activePresetId = settings.activePresetId
                    )
                }
            }
        }
    }

    private fun observeEngineTelemetry() {
        viewModelScope.launch {
            combine(
                audioEngine.diagnostics,
                audioEngine.positionMs,
                audioEngine.sinkRmsMap,
                audioEngine.sinkDriftMsMap
            ) { diag, pos, rmsMap, driftMap ->
                TelemetrySnapshot(diag, pos, rmsMap, driftMap)
            }.collect { snap ->
                _uiState.update { state ->
                    state.copy(
                        positionMs = snap.pos,
                        diagnostics = snap.diag,
                        outputDevices = state.outputDevices.map { dev ->
                            dev.copy(
                                rmsLevel = snap.rmsMap[dev.id] ?: 0f,
                                measuredDriftMs = snap.driftMap[dev.id] ?: 0f
                            )
                        }
                    )
                }
            }
        }
    }

    private fun pushRoutingTargetsToEngine(
        devices: List<AudioOutputDevice>,
        infoMap: Map<String, AudioDeviceInfo>
    ) {
        val targets = devices.mapNotNull { dev ->
            if (!dev.isConnected || !dev.isEnabledInParty) return@mapNotNull null
            val info = infoMap[dev.id] ?: return@mapNotNull null
            SinkRoutingTarget(
                stableId = dev.id,
                audioDeviceInfo = info,
                volume = dev.volume,
                isMuted = dev.isMuted,
                latencyOffsetMs = dev.latencyOffsetMs
            )
        }
        audioEngine.updateTargets(targets)
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val enabledConnected = _uiState.value.outputDevices.count { it.isConnected && it.isEnabledInParty }
        if (enabledConnected == 0) {
            _uiState.update {
                it.copy(statusBanner = "Enable at least one connected speaker to start synchronized playback.")
            }
            return
        }
        pushRoutingTargetsToEngine(_uiState.value.outputDevices, latestAudioDeviceMap.value)
        audioEngine.play()
        _uiState.update { it.copy(isPlaying = true, statusBanner = null) }
        PartyAudioService.updateNotification(
            context = appContext,
            trackTitle = _uiState.value.currentTrack.title,
            sinkCount = enabledConnected,
            isPlaying = true
        )
    }

    fun pausePlayback(banner: String? = null) {
        audioEngine.pause()
        _uiState.update { it.copy(isPlaying = false, statusBanner = banner ?: it.statusBanner) }
        PartyAudioService.updateNotification(
            context = appContext,
            trackTitle = _uiState.value.currentTrack.title,
            sinkCount = _uiState.value.outputDevices.count { it.isConnected && it.isEnabledInParty },
            isPlaying = false
        )
    }

    fun selectTrack(track: PartyTrack, autoPlay: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingTrack = true, calibrationPulseActive = false) }
            val loaded: PartyTrack? = when (track.sourceType) {
                TrackSourceType.SYNTH_GROOVE -> {
                    val preset = track.synthPreset ?: SynthGroovePreset.NEON_PULSE
                    _currentAlbumArt.value = null
                    audioEngine.selectSynthTrack(preset)
                }
                TrackSourceType.LOCAL_FILE -> {
                    val uri = track.uriString?.let { Uri.parse(it) }
                    if (uri != null) {
                        _currentAlbumArt.value = audioEngine.loadAlbumArtBytes(uri)
                        audioEngine.loadLocalTrack(uri)
                    } else null
                }
            }
            if (loaded != null) {
                _uiState.update {
                    it.copy(
                        currentTrack = loaded,
                        isLoadingTrack = false,
                        positionMs = 0L,
                        statusBanner = null
                    )
                }
                if (autoPlay && !_uiState.value.isPlaying) {
                    launch(Dispatchers.Main) { startPlayback() }
                } else if (_uiState.value.isPlaying) {
                    PartyAudioService.updateNotification(
                        context = appContext,
                        trackTitle = loaded.title,
                        sinkCount = _uiState.value.outputDevices.count { d -> d.isConnected && d.isEnabledInParty },
                        isPlaying = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingTrack = false,
                        statusBanner = "Could not open audio file. Try MP3, WAV, AAC, or FLAC."
                    )
                }
            }
        }
    }

    fun loadLocalAudioUri(uri: Uri) {
        loadLocalAudioUris(listOf(uri))
    }

    fun loadLocalAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingTrack = true, calibrationPulseActive = false) }
            val extracted = uris.mapNotNull { uri ->
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                audioEngine.extractTrackMetadata(uri)
            }
            if (extracted.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoadingTrack = false,
                        statusBanner = "Unable to read selected audio file(s)."
                    )
                }
                return@launch
            }

            val existingLocal = latestSettings.importedTracks
            val mergedLocal = (extracted + existingLocal.filterNot { prev ->
                extracted.any { it.id == prev.id }
            })
            prefsRepo.saveImportedTracks(mergedLocal)

            val firstTrack = extracted.first()
            val firstUri = firstTrack.uriString?.let { Uri.parse(it) }
            val loaded = firstUri?.let {
                _currentAlbumArt.value = audioEngine.loadAlbumArtBytes(it)
                audioEngine.loadLocalTrack(it)
            } ?: firstTrack

            _uiState.update { state ->
                state.copy(
                    currentTrack = loaded,
                    playlist = mergedLocal + builtInPlaylist,
                    isLoadingTrack = false,
                    positionMs = 0L,
                    statusBanner = if (extracted.size == 1) {
                        "Added \"${loaded.title}\" to Music Player"
                    } else {
                        "Added ${extracted.size} songs to Music Player"
                    }
                )
            }
            if (!_uiState.value.isPlaying && _uiState.value.outputDevices.any { it.isConnected && it.isEnabledInParty }) {
                launch(Dispatchers.Main) { startPlayback() }
            }
        }
    }

    fun removeImportedTrack(trackId: String) {
        val remainingLocal = latestSettings.importedTracks.filterNot { it.id == trackId }
        viewModelScope.launch {
            prefsRepo.saveImportedTracks(remainingLocal)
        }
        val wasCurrent = _uiState.value.currentTrack.id == trackId
        val nextList = remainingLocal + builtInPlaylist
        _uiState.update { it.copy(playlist = nextList) }
        if (wasCurrent && nextList.isNotEmpty()) {
            selectTrack(nextList.first())
        }
    }

    fun toggleShuffle() {
        val next = !_uiState.value.isShuffleEnabled
        _uiState.update { it.copy(isShuffleEnabled = next) }
        viewModelScope.launch { prefsRepo.saveShuffleEnabled(next) }
    }

    fun cycleRepeatMode() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
        }
        audioEngine.setRepeatOne(next == RepeatMode.REPEAT_ONE)
        _uiState.update { it.copy(repeatMode = next) }
        viewModelScope.launch { prefsRepo.saveRepeatMode(next) }
    }

    fun setLibraryFilter(filter: MusicLibraryFilter) {
        _uiState.update { it.copy(libraryFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun handleTrackCompleted() {
        val state = _uiState.value
        val list = state.playlist
        if (list.isEmpty()) return
        val currentIdx = list.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0)
        val nextIdx = selectNextTrackIndex(
            currentIndex = currentIdx,
            playlistSize = list.size,
            isShuffleEnabled = state.isShuffleEnabled,
            repeatMode = state.repeatMode,
            autoAdvance = true
        )
        if (nextIdx == null) {
            pausePlayback()
            audioEngine.seekToMs(0L)
        } else {
            selectTrack(list[nextIdx])
        }
    }

    fun nextTrack() {
        val state = _uiState.value
        val list = state.playlist
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0)
        val nextIdx = selectNextTrackIndex(
            currentIndex = idx,
            playlistSize = list.size,
            isShuffleEnabled = state.isShuffleEnabled,
            repeatMode = state.repeatMode,
            autoAdvance = false
        ) ?: ((idx + 1) % list.size)
        selectTrack(list[nextIdx])
    }

    fun previousTrack() {
        val state = _uiState.value
        if (state.currentTrack.sourceType == TrackSourceType.LOCAL_FILE && state.positionMs > 4000L) {
            seekToMs(0L)
            return
        }
        val list = state.playlist
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == state.currentTrack.id }
        val prev = list[if (idx <= 0) list.lastIndex else idx - 1]
        selectTrack(prev)
    }

    fun seekToMs(ms: Long) {
        audioEngine.seekToMs(ms)
    }

    fun toggleCalibrationPulse() {
        if (_uiState.value.calibrationPulseActive) {
            val restore = previousTrackBeforeCalibration ?: builtInPlaylist.first()
            previousTrackBeforeCalibration = null
            _uiState.update { it.copy(calibrationPulseActive = false) }
            selectTrack(restore)
        } else {
            previousTrackBeforeCalibration = _uiState.value.currentTrack
            _currentAlbumArt.value = null
            val clickTrack = audioEngine.selectSynthTrack(SynthGroovePreset.CALIBRATION_CLICK)
            _uiState.update {
                it.copy(
                    calibrationPulseActive = true,
                    currentTrack = clickTrack,
                    statusBanner = "Calibration Pulse active — adjust speaker ms offsets until clicks lock together."
                )
            }
            if (!_uiState.value.isPlaying) {
                startPlayback()
            }
        }
    }

    fun setMasterVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val nextMuted = if (_uiState.value.isMasterMuted && clamped > 0f) false else _uiState.value.isMasterMuted
        audioEngine.updateMasterVolume(clamped, nextMuted)
        _uiState.update { it.copy(masterVolume = clamped, isMasterMuted = nextMuted) }
        viewModelScope.launch { prefsRepo.saveMasterVolume(clamped) }
    }

    fun toggleMasterMute() {
        val nextMuted = !_uiState.value.isMasterMuted
        audioEngine.updateMasterVolume(_uiState.value.masterVolume, nextMuted)
        _uiState.update { it.copy(isMasterMuted = nextMuted) }
    }

    fun toggleLowLatencyMode() {
        val next = !_uiState.value.lowLatencyMode
        audioEngine.setLowLatencyMode(next)
        viewModelScope.launch { prefsRepo.saveLowLatencyMode(next) }
    }

    fun toggleAutoPauseOnDisconnect() {
        val next = !_uiState.value.autoPauseOnDisconnect
        _uiState.update { it.copy(autoPauseOnDisconnect = next) }
        viewModelScope.launch { prefsRepo.saveAutoPauseOnDisconnect(next) }
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        audioEngine.setEqualizerPreset(preset)
        _uiState.update { it.copy(equalizerPreset = preset) }
        viewModelScope.launch { prefsRepo.saveEqualizerPreset(preset) }
    }

    fun setDeviceVolume(deviceId: String, volume: Float) {
        val dev = _uiState.value.outputDevices.find { it.id == deviceId } ?: return
        val clamped = volume.coerceIn(0f, 1f)
        val unmuteOnDrag = if (dev.isMuted && clamped > 0f) false else dev.isMuted
        if (dev.isMuted && !unmuteOnDrag && soloDeviceId.value != null && soloDeviceId.value != deviceId) {
            soloDeviceId.value = null
        }
        saveDeviceConfigUpdate(dev.copy(volume = clamped, isMuted = unmuteOnDrag))
    }

    fun setDiscoveredSpeakerVolume(address: String, name: String, volume: Float) {
        val matched = findOutputDeviceForBluetooth(address, name)
        if (matched != null) {
            setDeviceVolume(matched.id, volume)
        } else {
            val fallbackId = "bt_$address"
            val clamped = volume.coerceIn(0f, 1f)
            val existing = latestSettings.deviceConfigs[fallbackId]
            viewModelScope.launch {
                prefsRepo.saveDeviceConfig(
                    SavedDeviceConfig(
                        deviceId = fallbackId,
                        volume = clamped,
                        isMuted = false,
                        latencyOffsetMs = existing?.latencyOffsetMs ?: 0,
                        roomZone = existing?.roomZone ?: RoomZone.LIVING_ROOM,
                        isEnabledInParty = existing?.isEnabledInParty ?: true
                    )
                )
            }
        }
    }

    fun toggleDiscoveredSpeakerMute(address: String, name: String) {
        val matched = findOutputDeviceForBluetooth(address, name)
        if (matched != null) {
            toggleDeviceMute(matched.id)
        } else {
            val fallbackId = "bt_$address"
            val existing = latestSettings.deviceConfigs[fallbackId]
            viewModelScope.launch {
                prefsRepo.saveDeviceConfig(
                    SavedDeviceConfig(
                        deviceId = fallbackId,
                        volume = existing?.volume ?: 0.85f,
                        isMuted = !(existing?.isMuted ?: false),
                        latencyOffsetMs = existing?.latencyOffsetMs ?: 0,
                        roomZone = existing?.roomZone ?: RoomZone.LIVING_ROOM,
                        isEnabledInParty = existing?.isEnabledInParty ?: true
                    )
                )
            }
        }
    }

    private fun findOutputDeviceForBluetooth(address: String, name: String): AudioOutputDevice? {
        val list = _uiState.value.outputDevices
        return list.find { it.address.equals(address, ignoreCase = true) }
            ?: list.find { it.id == "bt_$address" }
            ?: list.find { it.isConnected && it.name.equals(name, ignoreCase = true) }
    }

    fun toggleDeviceMute(deviceId: String) {
        val dev = _uiState.value.outputDevices.find { it.id == deviceId } ?: return
        if (soloDeviceId.value != null && soloDeviceId.value != deviceId) {
            soloDeviceId.value = null
        }
        val currentSavedMute = latestSettings.deviceConfigs[deviceId]?.isMuted ?: false
        saveDeviceConfigUpdate(dev.copy(isMuted = !currentSavedMute))
    }

    fun toggleDeviceSolo(deviceId: String) {
        soloDeviceId.value = if (soloDeviceId.value == deviceId) null else deviceId
    }

    fun setDeviceLatencyOffset(deviceId: String, offsetMs: Int) {
        val dev = _uiState.value.outputDevices.find { it.id == deviceId } ?: return
        saveDeviceConfigUpdate(dev.copy(latencyOffsetMs = offsetMs.coerceIn(-150, 150)))
    }

    fun setDeviceRoomZone(deviceId: String, zone: RoomZone) {
        val dev = _uiState.value.outputDevices.find { it.id == deviceId } ?: return
        saveDeviceConfigUpdate(dev.copy(roomZone = zone))
    }

    fun toggleDeviceInParty(deviceId: String) {
        val dev = _uiState.value.outputDevices.find { it.id == deviceId } ?: return
        saveDeviceConfigUpdate(dev.copy(isEnabledInParty = !dev.isEnabledInParty))
    }

    private fun saveDeviceConfigUpdate(updated: AudioOutputDevice) {
        // Optimistic immediate UI & engine update
        val nextList = _uiState.value.outputDevices.map {
            if (it.id == updated.id) updated else it
        }
        _uiState.update { it.copy(outputDevices = nextList) }
        pushRoutingTargetsToEngine(nextList, latestAudioDeviceMap.value)

        viewModelScope.launch {
            prefsRepo.saveDeviceConfig(
                SavedDeviceConfig(
                    deviceId = updated.id,
                    volume = updated.volume,
                    isMuted = updated.isMuted,
                    latencyOffsetMs = updated.latencyOffsetMs,
                    roomZone = updated.roomZone,
                    isEnabledInParty = updated.isEnabledInParty
                )
            )
        }
    }

    fun saveCurrentAsPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val configs = _uiState.value.outputDevices.associate { dev ->
            dev.id to SavedDeviceConfig(
                deviceId = dev.id,
                volume = dev.volume,
                isMuted = dev.isMuted,
                latencyOffsetMs = dev.latencyOffsetMs,
                roomZone = dev.roomZone,
                isEnabledInParty = dev.isEnabledInParty
            )
        }
        val preset = PartyPreset(
            id = "preset_${UUID.randomUUID().toString().take(8)}",
            name = trimmed,
            masterVolume = _uiState.value.masterVolume,
            lowLatencyMode = _uiState.value.lowLatencyMode,
            deviceConfigs = configs,
            equalizerPreset = _uiState.value.equalizerPreset
        )
        val updatedPresets = _uiState.value.presets + preset
        viewModelScope.launch {
            prefsRepo.savePresets(updatedPresets, preset.id)
        }
        _uiState.update { it.copy(statusBanner = "Saved room preset \"$trimmed\"") }
    }

    fun applyPreset(preset: PartyPreset) {
        audioEngine.setEqualizerPreset(preset.equalizerPreset)
        viewModelScope.launch {
            prefsRepo.saveMasterVolume(preset.masterVolume)
            prefsRepo.saveLowLatencyMode(preset.lowLatencyMode)
            prefsRepo.saveEqualizerPreset(preset.equalizerPreset)
            if (preset.deviceConfigs.isNotEmpty()) {
                val merged = latestSettings.deviceConfigs.toMutableMap()
                merged.putAll(preset.deviceConfigs)
                prefsRepo.saveAllDeviceConfigs(merged)
            }
            prefsRepo.setActivePreset(preset.id)
        }
        _uiState.update { it.copy(statusBanner = "Applied \"${preset.name}\"") }
    }

    fun deletePreset(presetId: String) {
        val updated = _uiState.value.presets.filterNot { it.id == presetId }
        val nextActive = if (_uiState.value.activePresetId == presetId) null else _uiState.value.activePresetId
        viewModelScope.launch {
            prefsRepo.savePresets(updated, nextActive)
        }
    }

    fun onBluetoothPermissionsUpdated() {
        bluetoothMonitor.attachProfileProxies()
        bluetoothMonitor.refreshAll()
    }

    fun setDiscoveryAudioOnlyFilter(audioOnly: Boolean) {
        _uiState.update { it.copy(discoveryAudioOnlyFilter = audioOnly) }
    }

    fun startBluetoothScanIfIdle() {
        if (!_uiState.value.isScanning) {
            toggleBluetoothScan()
        }
    }

    fun toggleBluetoothScan() {
        if (_uiState.value.isScanning) {
            bluetoothMonitor.stopScan()
        } else {
            val started = bluetoothMonitor.startScan()
            if (!started) {
                _uiState.update {
                    it.copy(statusBanner = "Enable Bluetooth & grant scan permission to discover nearby speakers.")
                }
            }
        }
    }

    fun pairDiscoveredDevice(address: String) {
        val ok = bluetoothMonitor.initiateBond(address)
        _uiState.update {
            it.copy(
                statusBanner = if (ok) "Pairing request sent — confirm on your speaker if prompted."
                else "Unable to pair directly — opening Bluetooth settings may be needed."
            )
        }
    }

    fun openBluetoothSettings() {
        bluetoothMonitor.openBluetoothSettings()
    }

    fun refreshDevices() {
        bluetoothMonitor.refreshAll()
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothMonitor.stop()
        audioEngine.release()
    }

    private data class SystemSnapshot(
        val sysDevices: List<AudioDeviceInfo>,
        val pairedBt: List<com.example.bluetooth.PairedBluetoothSpeaker>,
        val discovered: List<com.example.model.DiscoveredBluetoothDevice>,
        val scanning: Boolean,
        val caps: com.example.model.BluetoothCapabilities
    )

    private data class TelemetrySnapshot(
        val diag: com.example.model.EngineDiagnostics,
        val pos: Long,
        val rmsMap: Map<String, Float>,
        val driftMap: Map<String, Float>
    )

    companion object {
        fun selectNextTrackIndex(
            currentIndex: Int,
            playlistSize: Int,
            isShuffleEnabled: Boolean,
            repeatMode: RepeatMode,
            autoAdvance: Boolean,
            random: Random = Random.Default
        ): Int? {
            if (playlistSize <= 0) return null
            if (playlistSize == 1) {
                return if (!autoAdvance || repeatMode != RepeatMode.OFF) 0 else null
            }
            if (isShuffleEnabled) {
                val candidates = (0 until playlistSize).filter { it != currentIndex }
                return candidates[random.nextInt(candidates.size)]
            }
            val next = currentIndex + 1
            return if (next < playlistSize) {
                next
            } else if (!autoAdvance || repeatMode == RepeatMode.REPEAT_ALL) {
                0
            } else {
                null
            }
        }
    }
}
