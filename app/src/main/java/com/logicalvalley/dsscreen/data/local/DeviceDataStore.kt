package com.logicalvalley.dsscreen.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_registration")

/**
 * DataStore manager for device registration data
 */
class DeviceDataStore(private val context: Context) {
    companion object {
        private val DEVICE_UID = stringPreferencesKey("device_uid")
        private val PLAYLIST_CODE = stringPreferencesKey("playlist_code")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val PLAYLIST_ID = stringPreferencesKey("playlist_id")
        private val PLAYLIST_NAME = stringPreferencesKey("playlist_name")
        private val REGISTERED_AT = stringPreferencesKey("registered_at")
        private val PLAYLIST_DATA = stringPreferencesKey("playlist_data")
    }

    /**
     * Save device registration data
     */
    suspend fun saveRegistration(
        deviceUid: String,
        playlistCode: String,
        deviceId: String,
        playlistId: String,
        playlistName: String,
        registeredAt: String,
        playlistData: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_UID] = deviceUid
            preferences[PLAYLIST_CODE] = playlistCode
            preferences[DEVICE_ID] = deviceId
            preferences[PLAYLIST_ID] = playlistId
            preferences[PLAYLIST_NAME] = playlistName
            preferences[REGISTERED_AT] = registeredAt
            preferences[PLAYLIST_DATA] = playlistData
        }
    }

    /**
     * Get device UID
     */
    fun getDeviceUid(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[DEVICE_UID]
        }
    }

    /**
     * Get playlist code
     */
    fun getPlaylistCode(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[PLAYLIST_CODE]
        }
    }

    /**
     * Check if device is registered
     */
    fun isRegistered(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DEVICE_ID] != null && preferences[PLAYLIST_ID] != null
        }
    }

    /**
     * Get all registration data as a map
     */
    fun getRegistrationData(): Flow<Map<String, String?>> {
        return context.dataStore.data.map { preferences ->
            mapOf(
                "deviceUid" to preferences[DEVICE_UID],
                "playlistCode" to preferences[PLAYLIST_CODE],
                "deviceId" to preferences[DEVICE_ID],
                "playlistId" to preferences[PLAYLIST_ID],
                "playlistName" to preferences[PLAYLIST_NAME],
                "registeredAt" to preferences[REGISTERED_AT]
            )
        }
    }

    /**
     * Get stored playlist data (JSON)
     */
    fun getPlaylistData(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[PLAYLIST_DATA]
        }
    }

    /**
     * Clear registration data (for re-registration)
     */
    suspend fun clearRegistration() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

