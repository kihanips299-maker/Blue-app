package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.EqualizerPreset
import com.example.model.PartyPreset
import com.example.model.PartyTrack
import com.example.model.RepeatMode
import com.example.model.RoomZone
import com.example.model.SavedDeviceConfig
import com.example.model.TrackSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ampsync_prefs")

data class PersistedSettings(
    val masterVolume: Float = 0.85f,
    val lowLatencyMode: Boolean = true,
    val autoPauseOnDisconnect: Boolean = true,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL,
    val importedTracks: List<PartyTrack> = emptyList(),
    val deviceConfigs: Map<String, SavedDeviceConfig> = emptyMap(),
    val presets: List<PartyPreset> = defaultPresets(),
    val activePresetId: String? = null
)

fun defaultPresets(): List<PartyPreset> = listOf(
    PartyPreset(
        id = "preset_house_party",
        name = "House Party Boost",
        masterVolume = 0.90f,
        lowLatencyMode = true,
        deviceConfigs = emptyMap(),
        equalizerPreset = EqualizerPreset.PARTY
    ),
    PartyPreset(
        id = "preset_chill_lounge",
        name = "Chill Terrace",
        masterVolume = 0.65f,
        lowLatencyMode = true,
        deviceConfigs = emptyMap(),
        equalizerPreset = EqualizerPreset.ACOUSTIC
    ),
    PartyPreset(
        id = "preset_tight_sync",
        name = "Zero-Drift Sync",
        masterVolume = 0.85f,
        lowLatencyMode = true,
        deviceConfigs = emptyMap(),
        equalizerPreset = EqualizerPreset.FLAT
    )
)

class PartyPreferencesRepository(private val context: Context) {

    private object Keys {
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val LOW_LATENCY_MODE = booleanPreferencesKey("low_latency_mode")
        val AUTO_PAUSE_ON_DISCONNECT = booleanPreferencesKey("auto_pause_on_disconnect")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val IMPORTED_TRACKS_JSON = stringPreferencesKey("imported_tracks_json")
        val DEVICE_CONFIGS_JSON = stringPreferencesKey("device_configs_json")
        val PRESETS_JSON = stringPreferencesKey("presets_json")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
    }

    val settingsFlow: Flow<PersistedSettings> = context.dataStore.data.map { prefs ->
        val configs = parseDeviceConfigs(prefs[Keys.DEVICE_CONFIGS_JSON])
        val savedPresets = parsePresets(prefs[Keys.PRESETS_JSON])
        val eqName = prefs[Keys.EQUALIZER_PRESET]
        val eqPreset = EqualizerPreset.entries.find { it.name == eqName } ?: EqualizerPreset.FLAT
        val repeatName = prefs[Keys.REPEAT_MODE]
        val repeatMode = RepeatMode.entries.find { it.name == repeatName } ?: RepeatMode.REPEAT_ALL
        val importedTracks = parseTracks(prefs[Keys.IMPORTED_TRACKS_JSON])
        PersistedSettings(
            masterVolume = prefs[Keys.MASTER_VOLUME] ?: 0.85f,
            lowLatencyMode = prefs[Keys.LOW_LATENCY_MODE] ?: true,
            autoPauseOnDisconnect = prefs[Keys.AUTO_PAUSE_ON_DISCONNECT] ?: true,
            equalizerPreset = eqPreset,
            isShuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = repeatMode,
            importedTracks = importedTracks,
            deviceConfigs = configs,
            presets = savedPresets.ifEmpty { defaultPresets() },
            activePresetId = prefs[Keys.ACTIVE_PRESET_ID]
        )
    }

    suspend fun saveMasterVolume(volume: Float) {
        context.dataStore.edit { it[Keys.MASTER_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun saveLowLatencyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOW_LATENCY_MODE] = enabled }
    }

    suspend fun saveAutoPauseOnDisconnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PAUSE_ON_DISCONNECT] = enabled }
    }

    suspend fun saveEqualizerPreset(preset: EqualizerPreset) {
        context.dataStore.edit { it[Keys.EQUALIZER_PRESET] = preset.name }
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun saveRepeatMode(mode: RepeatMode) {
        context.dataStore.edit { it[Keys.REPEAT_MODE] = mode.name }
    }

    suspend fun saveImportedTracks(tracks: List<PartyTrack>) {
        context.dataStore.edit { it[Keys.IMPORTED_TRACKS_JSON] = encodeTracks(tracks) }
    }

    suspend fun saveDeviceConfig(config: SavedDeviceConfig) {
        context.dataStore.edit { prefs ->
            val current = parseDeviceConfigs(prefs[Keys.DEVICE_CONFIGS_JSON]).toMutableMap()
            current[config.deviceId] = config
            prefs[Keys.DEVICE_CONFIGS_JSON] = encodeDeviceConfigs(current)
        }
    }

    suspend fun saveAllDeviceConfigs(configs: Map<String, SavedDeviceConfig>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_CONFIGS_JSON] = encodeDeviceConfigs(configs)
        }
    }

    suspend fun savePresets(presets: List<PartyPreset>, activePresetId: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRESETS_JSON] = encodePresets(presets)
            if (activePresetId != null) {
                prefs[Keys.ACTIVE_PRESET_ID] = activePresetId
            } else {
                prefs.remove(Keys.ACTIVE_PRESET_ID)
            }
        }
    }

    suspend fun setActivePreset(activePresetId: String?) {
        context.dataStore.edit { prefs ->
            if (activePresetId != null) {
                prefs[Keys.ACTIVE_PRESET_ID] = activePresetId
            } else {
                prefs.remove(Keys.ACTIVE_PRESET_ID)
            }
        }
    }

    private fun encodeDeviceConfigs(configs: Map<String, SavedDeviceConfig>): String {
        val arr = JSONArray()
        configs.values.forEach { cfg ->
            arr.put(JSONObject().apply {
                put("deviceId", cfg.deviceId)
                put("volume", cfg.volume.toDouble())
                put("isMuted", cfg.isMuted)
                put("latencyOffsetMs", cfg.latencyOffsetMs)
                put("roomZone", cfg.roomZone.name)
                put("isEnabledInParty", cfg.isEnabledInParty)
            })
        }
        return arr.toString()
    }

    private fun parseDeviceConfigs(raw: String?): Map<String, SavedDeviceConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val result = mutableMapOf<String, SavedDeviceConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val deviceId = obj.getString("deviceId")
                val zoneName = obj.optString("roomZone", RoomZone.LIVING_ROOM.name)
                val zone = RoomZone.entries.find { it.name == zoneName } ?: RoomZone.LIVING_ROOM
                result[deviceId] = SavedDeviceConfig(
                    deviceId = deviceId,
                    volume = obj.optDouble("volume", 0.85).toFloat().coerceIn(0f, 1f),
                    isMuted = obj.optBoolean("isMuted", false),
                    latencyOffsetMs = obj.optInt("latencyOffsetMs", 0).coerceIn(-150, 150),
                    roomZone = zone,
                    isEnabledInParty = obj.optBoolean("isEnabledInParty", true)
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun encodePresets(presets: List<PartyPreset>): String {
        val arr = JSONArray()
        presets.forEach { preset ->
            arr.put(JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("masterVolume", preset.masterVolume.toDouble())
                put("lowLatencyMode", preset.lowLatencyMode)
                put("equalizerPreset", preset.equalizerPreset.name)
                put("deviceConfigs", encodeDeviceConfigs(preset.deviceConfigs))
            })
        }
        return arr.toString()
    }

    private fun parsePresets(raw: String?): List<PartyPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val eqName = obj.optString("equalizerPreset", EqualizerPreset.FLAT.name)
                    val eqPreset = EqualizerPreset.entries.find { it.name == eqName } ?: EqualizerPreset.FLAT
                    add(
                        PartyPreset(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            masterVolume = obj.optDouble("masterVolume", 0.85).toFloat().coerceIn(0f, 1f),
                            lowLatencyMode = obj.optBoolean("lowLatencyMode", true),
                            deviceConfigs = parseDeviceConfigs(obj.optString("deviceConfigs")),
                            equalizerPreset = eqPreset
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeTracks(tracks: List<PartyTrack>): String {
        val arr = JSONArray()
        tracks.forEach { track ->
            if (track.sourceType == TrackSourceType.LOCAL_FILE && !track.uriString.isNullOrBlank()) {
                arr.put(JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("bpm", track.bpm)
                    put("durationMs", track.durationMs)
                    put("uriString", track.uriString)
                })
            }
        }
        return arr.toString()
    }

    private fun parseTracks(raw: String?): List<PartyTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uriString = obj.optString("uriString").takeIf { it.isNotBlank() } ?: continue
                    add(
                        PartyTrack(
                            id = obj.getString("id"),
                            title = obj.optString("title", "Local Audio"),
                            artist = obj.optString("artist", "Local Audio"),
                            album = obj.optString("album", "Device Library"),
                            bpm = obj.optInt("bpm", 120),
                            durationMs = obj.optLong("durationMs", 0L),
                            sourceType = TrackSourceType.LOCAL_FILE,
                            uriString = uriString
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
