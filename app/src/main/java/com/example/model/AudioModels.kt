package com.example.model

enum class SpeakerTransportType(val label: String, val defaultBufferLatencyMs: Int) {
    BLUETOOTH_A2DP("BT A2DP", 95),
    BLUETOOTH_LE_AUDIO("LE AUDIO", 22),
    BLUETOOTH_LE_BROADCAST("AURACAST", 18),
    WIRED_AUDIO("WIRED", 8),
    USB_AUDIO("USB DAC", 10),
    BUILT_IN_SPEAKER("PHONE", 12)
}

enum class RoomZone(val displayName: String) {
    LIVING_ROOM("Living Room"),
    KITCHEN("Kitchen"),
    PATIO("Patio"),
    DINING("Dining"),
    BASEMENT("Basement"),
    STUDIO("Studio")
}

data class AudioOutputDevice(
    val id: String,
    val systemDeviceId: Int?,
    val name: String,
    val address: String?,
    val transportType: SpeakerTransportType,
    val isConnected: Boolean,
    val isPaired: Boolean,
    val isEnabledInParty: Boolean,
    val volume: Float,
    val isMuted: Boolean,
    val isSolo: Boolean,
    val latencyOffsetMs: Int,
    val roomZone: RoomZone,
    val accentIndex: Int,
    val supportedSampleRates: List<Int> = emptyList(),
    val rmsLevel: Float = 0f,
    val measuredDriftMs: Float = 0f,
    val batteryPercent: Int? = null
)

enum class BluetoothConnectionStatus(val label: String) {
    CONNECTED("Connected"),
    CONNECTING("Connecting"),
    PAIRED("Paired"),
    PAIRING("Pairing"),
    AVAILABLE("Available")
}

enum class SignalStrengthLevel(val label: String, val bars: Int) {
    EXCELLENT("Excellent", 4),
    GOOD("Good", 3),
    FAIR("Fair", 2),
    WEAK("Weak", 1),
    UNKNOWN("Link Active", 0)
}

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val rssi: Int?,
    val isAudioDevice: Boolean,
    val bondState: Int,
    val connectionStatus: BluetoothConnectionStatus = BluetoothConnectionStatus.AVAILABLE,
    val transportHint: String = "Classic BT",
    val lastSeenMs: Long = 0L,
    val batteryPercent: Int? = null
) {
    val signalLevel: SignalStrengthLevel
        get() = when {
            rssi == null -> SignalStrengthLevel.UNKNOWN
            rssi >= -55 -> SignalStrengthLevel.EXCELLENT
            rssi >= -68 -> SignalStrengthLevel.GOOD
            rssi >= -80 -> SignalStrengthLevel.FAIR
            else -> SignalStrengthLevel.WEAK
        }
}

enum class EqualizerPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val bandGainsDb: List<Float>
) {
    FLAT(
        id = "flat",
        title = "Flat",
        subtitle = "Neutral studio reference",
        bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f)
    ),
    PARTY(
        id = "party",
        title = "Party",
        subtitle = "Punchy sub & sparkling highs",
        bandGainsDb = listOf(5f, 2f, -1f, 3f, 5f)
    ),
    ACOUSTIC(
        id = "acoustic",
        title = "Acoustic",
        subtitle = "Warm mids & natural presence",
        bandGainsDb = listOf(2f, 1f, 3f, 3f, 2f)
    ),
    BASS_BOOST(
        id = "bass_boost",
        title = "Bass Boost",
        subtitle = "Deep sub-bass & kick slam",
        bandGainsDb = listOf(8f, 5f, 0f, -1f, 1f)
    ),
    VOCAL(
        id = "vocal",
        title = "Vocal",
        subtitle = "Clear speech & lyric focus",
        bandGainsDb = listOf(-2f, 0f, 4f, 5f, 2f)
    ),
    OUTDOOR(
        id = "outdoor",
        title = "Outdoor",
        subtitle = "Open-air loudness projection",
        bandGainsDb = listOf(4f, 1f, 2f, 4f, 6f)
    );

    companion object {
        val BAND_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k")
        val BAND_FREQUENCIES_HZ = listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
    }
}

enum class SynthGroovePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val bpm: Int
) {
    NEON_PULSE(
        id = "synth_neon_pulse",
        title = "Neon Pulse",
        subtitle = "Deep House Groove • 44.1kHz Synth",
        bpm = 124
    ),
    MIDNIGHT_BASS(
        id = "synth_midnight_bass",
        title = "Midnight Bassline",
        subtitle = "Club Sub Drive • 44.1kHz Synth",
        bpm = 128
    ),
    SUNSET_LOUNGE(
        id = "synth_sunset_lounge",
        title = "Sunset Terrace",
        subtitle = "Warm Chill Rhodes • 44.1kHz Synth",
        bpm = 104
    ),
    ELECTRIC_EUPHORIA(
        id = "synth_electric_euphoria",
        title = "Electric Euphoria",
        subtitle = "Neon Synthwave • 44.1kHz Synth",
        bpm = 118
    ),
    ROOFTOP_GROOVE(
        id = "synth_rooftop_groove",
        title = "Rooftop Afrobeat",
        subtitle = "Percussive Poly-Groove • 44.1kHz Synth",
        bpm = 112
    ),
    CALIBRATION_CLICK(
        id = "synth_calibration_click",
        title = "Sync Calibration Pulse",
        subtitle = "Precision Acoustic Alignment Click",
        bpm = 120
    )
}

enum class RepeatMode(val label: String) {
    OFF("Repeat Off"),
    REPEAT_ALL("Repeat All"),
    REPEAT_ONE("Repeat One")
}

enum class MusicLibraryFilter(val label: String) {
    ALL("All Tracks"),
    LOCAL_MUSIC("My Music"),
    PARTY_GROOVES("Built-in Grooves")
}

enum class TrackSourceType {
    SYNTH_GROOVE,
    LOCAL_FILE
}

data class PartyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "AmpSync Library",
    val bpm: Int,
    val durationMs: Long,
    val sourceType: TrackSourceType,
    val uriString: String? = null,
    val synthPreset: SynthGroovePreset? = null
)

data class SavedDeviceConfig(
    val deviceId: String,
    val volume: Float,
    val isMuted: Boolean,
    val latencyOffsetMs: Int,
    val roomZone: RoomZone,
    val isEnabledInParty: Boolean
)

data class PartyPreset(
    val id: String,
    val name: String,
    val masterVolume: Float,
    val lowLatencyMode: Boolean,
    val deviceConfigs: Map<String, SavedDeviceConfig>,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT
)

data class BluetoothCapabilities(
    val bluetoothEnabled: Boolean = false,
    val hasBluetoothPermissions: Boolean = false,
    val leAudioSupported: Boolean = false,
    val auracastBroadcastSupported: Boolean = false,
    val activeA2dpCount: Int = 0,
    val activeLeAudioCount: Int = 0
)

data class EngineDiagnostics(
    val sampleRateHz: Int = 44100,
    val bufferFrames: Int = 1024,
    val performanceModeLabel: String = "LOW_LATENCY",
    val activeSinkCount: Int = 0,
    val maxSyncDriftMs: Float = 0f,
    val hasAudioFocus: Boolean = false,
    val masterRms: Float = 0f,
    val spectrumBands: List<Float> = List(16) { 0f }
)

data class PartyUiState(
    val isPlaying: Boolean = false,
    val isLoadingTrack: Boolean = false,
    val masterVolume: Float = 0.85f,
    val isMasterMuted: Boolean = false,
    val lowLatencyMode: Boolean = true,
    val autoPauseOnDisconnect: Boolean = true,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL,
    val libraryFilter: MusicLibraryFilter = MusicLibraryFilter.ALL,
    val searchQuery: String = "",
    val currentTrack: PartyTrack = PartyTrack(
        id = SynthGroovePreset.NEON_PULSE.id,
        title = SynthGroovePreset.NEON_PULSE.title,
        artist = SynthGroovePreset.NEON_PULSE.subtitle,
        bpm = SynthGroovePreset.NEON_PULSE.bpm,
        durationMs = 0L,
        sourceType = TrackSourceType.SYNTH_GROOVE,
        synthPreset = SynthGroovePreset.NEON_PULSE
    ),
    val playlist: List<PartyTrack> = emptyList(),
    val positionMs: Long = 0L,
    val outputDevices: List<AudioOutputDevice> = emptyList(),
    val discoveredDevices: List<DiscoveredBluetoothDevice> = emptyList(),
    val discoveryAudioOnlyFilter: Boolean = true,
    val isScanning: Boolean = false,
    val bluetoothCapabilities: BluetoothCapabilities = BluetoothCapabilities(),
    val diagnostics: EngineDiagnostics = EngineDiagnostics(),
    val presets: List<PartyPreset> = emptyList(),
    val activePresetId: String? = null,
    val statusBanner: String? = null,
    val calibrationPulseActive: Boolean = false
)
