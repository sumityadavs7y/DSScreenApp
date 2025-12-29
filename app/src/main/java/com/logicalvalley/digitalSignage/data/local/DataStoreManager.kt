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
}

