package com.logicalvalley.digitalSignage.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    companion object {
        val PLAYLIST_CODE = stringPreferencesKey("playlist_code")
        val PLAYLIST_ID = stringPreferencesKey("playlist_id")
        val DEVICE_UID = stringPreferencesKey("device_uid")
        val SAVED_PLAYLIST = stringPreferencesKey("saved_playlist")
        val LICENSE_EXPIRY = stringPreferencesKey("license_expiry")
        val SCREEN_ROTATION = stringPreferencesKey("screen_rotation")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
    }

    val playlistCode: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PLAYLIST_CODE]
        }

    suspend fun savePlaylistCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[PLAYLIST_CODE] = code
        }
    }

    val playlistId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PLAYLIST_ID]
        }

    suspend fun savePlaylistId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[PLAYLIST_ID] = id
        }
    }

    val deviceUid: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DEVICE_UID]
        }

    suspend fun saveDeviceUid(uid: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_UID] = uid
        }
    }

    val savedPlaylist: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SAVED_PLAYLIST]
        }

    suspend fun savePlaylist(playlistJson: String) {
        context.dataStore.edit { preferences ->
            preferences[SAVED_PLAYLIST] = playlistJson
        }
    }

    val licenseExpiry: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LICENSE_EXPIRY]
        }

    suspend fun saveLicenseExpiry(expiry: String) {
        context.dataStore.edit { preferences ->
            preferences[LICENSE_EXPIRY] = expiry
        }
    }

    val screenRotation: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SCREEN_ROTATION]
        }

    suspend fun saveScreenRotation(rotation: String) {
        context.dataStore.edit { preferences ->
            preferences[SCREEN_ROTATION] = rotation
        }
    }

    val displayMode: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DISPLAY_MODE]
        }

    suspend fun saveDisplayMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_MODE] = mode
        }
    }

    val CUSTOM_DISPLAY_MODES = stringPreferencesKey("custom_display_modes")

    val customDisplayModes: Flow<Map<String, String>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[CUSTOM_DISPLAY_MODES] ?: "{}"
            try {
                // Parse JSON string to Map
                parseCustomDisplayModes(json)
            } catch (e: Exception) {
                emptyMap()
            }
        }

    suspend fun saveCustomDisplayModes(modes: Map<String, String>) {
        context.dataStore.edit { preferences ->
            // Convert Map to JSON string
            val json = convertCustomDisplayModesToJson(modes)
            preferences[CUSTOM_DISPLAY_MODES] = json
        }
    }

    suspend fun saveItemDisplayMode(itemId: String, mode: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[CUSTOM_DISPLAY_MODES] ?: "{}"
            val currentModes = parseCustomDisplayModes(currentJson).toMutableMap()
            currentModes[itemId] = mode
            val newJson = convertCustomDisplayModesToJson(currentModes)
            preferences[CUSTOM_DISPLAY_MODES] = newJson
        }
    }

    private fun parseCustomDisplayModes(json: String): Map<String, String> {
        if (json.isEmpty() || json == "{}") return emptyMap()
        
        // Simple JSON parser for Map<String, String>
        val map = mutableMapOf<String, String>()
        val content = json.trim().removeSurrounding("{", "}")
        if (content.isEmpty()) return emptyMap()
        
        content.split(",").forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("\"")
                val value = parts[1].trim().removeSurrounding("\"")
                map[key] = value
            }
        }
        return map
    }

    private fun convertCustomDisplayModesToJson(modes: Map<String, String>): String {
        if (modes.isEmpty()) return "{}"
        
        val entries = modes.entries.joinToString(",") { (key, value) ->
            "\"$key\":\"$value\""
        }
        return "{$entries}"
    }
}


