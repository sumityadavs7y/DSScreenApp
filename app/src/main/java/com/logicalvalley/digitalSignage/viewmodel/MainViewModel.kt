package com.logicalvalley.digitalSignage.viewmodel

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.logicalvalley.digitalSignage.config.AppConfig
import com.logicalvalley.digitalSignage.data.api.RetrofitClient
import com.logicalvalley.digitalSignage.data.local.DataStoreManager
import com.logicalvalley.digitalSignage.data.local.MediaCacheManager
import com.logicalvalley.digitalSignage.data.model.Playlist
import com.logicalvalley.digitalSignage.data.model.PlaybackErrorInfo
import com.logicalvalley.digitalSignage.data.model.RegisterResponse
import com.logicalvalley.digitalSignage.data.model.TimelineResponse
import com.logicalvalley.digitalSignage.data.repository.LicenseExpiredException
import com.logicalvalley.digitalSignage.data.repository.TimelineLicenseExpiredException
import com.logicalvalley.digitalSignage.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

sealed class AppState {
    object Loading : AppState()
    object RegistrationRequired : AppState()
    object LicenseExpired : AppState()
    data class Playing(val playlist: Playlist, val cacheProgress: Float) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val repository = PlaylistRepository(RetrofitClient.apiService)
    private val dataStoreManager = DataStoreManager(application)
    private val cacheManager = MediaCacheManager(application)
    private val deviceUid = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
    private val baseUrl = AppConfig.BASE_URL
    private val gson = Gson()

    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _cacheProgress = MutableStateFlow(0f)
    val cacheProgress: StateFlow<Float> = _cacheProgress.asStateFlow()

    private val _licenseExpiryDate = MutableStateFlow<String?>(null)
    val licenseExpiryDate: StateFlow<String?> = _licenseExpiryDate.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackErrorInfo?>(null)
    val playbackError: StateFlow<PlaybackErrorInfo?> = _playbackError.asStateFlow()

    private var currentPlaylistJson: String? = null
    private var lastLicenseExpiry: Date? = null

    init {
        checkRegistration()
        startPeriodicCheck()
    }

    fun reportPlaybackError(videoName: String, error: String) {
        _playbackError.value = PlaybackErrorInfo(videoName, error, Date())
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    private fun isLicenseExpired(): Boolean {
        val expiry = lastLicenseExpiry ?: return false
        return Date().after(expiry)
    }

    private fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrEmpty() || dateStr == "null") return null
        val cleanedDate = dateStr.replace("\"", "").trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.US).apply {
                    if (format.contains("Z")) timeZone = TimeZone.getTimeZone("UTC")
                }.parse(cleanedDate)
            } catch (e: Exception) {}
        }
        return null
    }

    private fun checkRegistration() {
        Log.d(TAG, "🔍 Checking initial registration...")
        viewModelScope.launch {
            val savedCode = dataStoreManager.playlistCode.first()
            val savedPlaylistId = dataStoreManager.playlistId.first()
            val savedPlaylistJson = dataStoreManager.savedPlaylist.first()
            val savedLicenseExpiry = dataStoreManager.licenseExpiry.first()
            
            Log.d(TAG, "📂 Loaded from storage - Code: $savedCode, Id: $savedPlaylistId, LicenseExpiry: $savedLicenseExpiry")

            if (!savedLicenseExpiry.isNullOrEmpty() && savedLicenseExpiry != "null") {
                _licenseExpiryDate.value = savedLicenseExpiry
                lastLicenseExpiry = parseDate(savedLicenseExpiry)
                Log.d(TAG, "🗓️ Set lastLicenseExpiry to: $lastLicenseExpiry")
            }

            if (!savedCode.isNullOrEmpty() && !savedPlaylistId.isNullOrEmpty()) {
                if (isLicenseExpired()) {
                    Log.w(TAG, "⚠️ License already expired at startup")
                    _appState.value = AppState.LicenseExpired
                    refreshTimeline(savedPlaylistId)
                    return@launch
                }

                if (!savedPlaylistJson.isNullOrEmpty()) {
                    try {
                        Log.d(TAG, "▶️ Found saved playlist, starting playback...")
                        val playlist = gson.fromJson(savedPlaylistJson, Playlist::class.java)
                        currentPlaylistJson = savedPlaylistJson
                        _appState.value = AppState.Playing(playlist, cacheManager.getCacheProgress(playlist.items))
                        refreshTimeline(savedPlaylistId)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to parse saved playlist JSON", e)
                        // If parsing failed, we still have the ID, try timeline
                        refreshTimeline(savedPlaylistId)
                    }
                } else {
                    Log.d(TAG, "❓ No saved playlist JSON, calling timeline...")
                    refreshTimeline(savedPlaylistId)
                }
            } else if (!savedCode.isNullOrEmpty()) {
                // We have a code but no ID (older version), call register once to get ID
                register(savedCode)
            } else {
                Log.d(TAG, "👋 No saved code found, registration required.")
                _appState.value = AppState.RegistrationRequired
            }
        }
    }

    private fun startPeriodicCheck() {
        viewModelScope.launch {
            while (true) {
                delay(30000) // 30 seconds
                Log.d(TAG, "⏰ Periodic check triggered...")
                if (isLicenseExpired()) {
                    Log.w(TAG, "⚠️ Periodic check: License expired")
                    _appState.value = AppState.LicenseExpired
                }
                
                val savedPlaylistId = dataStoreManager.playlistId.first()
                if (!savedPlaylistId.isNullOrEmpty()) {
                    Log.d(TAG, "🔄 Refreshing timeline for id: $savedPlaylistId")
                    refreshTimeline(savedPlaylistId)
                }
            }
        }
    }

    private fun extractLicense(response: RegisterResponse): String? {
        Log.d(TAG, "📦 Full Register Response JSON: ${gson.toJson(response)}")
        val licenseObj = response.data?.license ?: response.topLevelLicense
        if (licenseObj == null) {
            Log.e(TAG, "❌ CRITICAL: License object is COMPLETELY MISSING from the Register API response!")
            return null
        }
        val expiry = licenseObj.expiresAt ?: licenseObj.expiresAtSnake
        Log.d(TAG, "🔑 Extracted license expiry: $expiry")
        return expiry
    }

    private fun extractLicenseFromTimeline(response: TimelineResponse): String? {
        Log.d(TAG, "📦 Full Timeline Response JSON: ${gson.toJson(response)}")
        val licenseObj = response.license
        if (licenseObj == null) {
            Log.e(TAG, "❌ CRITICAL: License object is COMPLETELY MISSING from the Timeline API response!")
            return null
        }
        val expiry = licenseObj.expiresAt ?: licenseObj.expiresAtSnake
        Log.d(TAG, "🔑 Extracted timeline license expiry: $expiry")
        return expiry
    }

    private fun refreshTimeline(playlistId: String) {
        viewModelScope.launch {
            Log.d(TAG, "🌐 Calling refreshTimeline API for: $playlistId")
            repository.getPlaylistTimeline(playlistId)
                .onSuccess { response ->
                    Log.d(TAG, "✅ Timeline API success")
                    val expiresAt = extractLicenseFromTimeline(response)
                    if (!expiresAt.isNullOrEmpty()) {
                        lastLicenseExpiry = parseDate(expiresAt)
                        _licenseExpiryDate.value = expiresAt
                        dataStoreManager.saveLicenseExpiry(expiresAt)
                        Log.d(TAG, "💾 Saved timeline license expiry: $expiresAt")
                    }

                    if (isLicenseExpired()) {
                        Log.w(TAG, "🚫 License is expired after timeline refresh")
                        _appState.value = AppState.LicenseExpired
                        return@onSuccess
                    }

                    if (response.success && response.items != null) {
                        // Get current playlist to update its items
                        val savedPlaylistJson = dataStoreManager.savedPlaylist.first()
                        if (!savedPlaylistJson.isNullOrEmpty()) {
                            val currentPlaylist = gson.fromJson(savedPlaylistJson, Playlist::class.java)
                            val newPlaylist = currentPlaylist.copy(items = response.items)
                            val newPlaylistJson = gson.toJson(newPlaylist)
                            
                            if (newPlaylistJson != currentPlaylistJson) {
                                Log.d(TAG, "🆕 Timeline changed! Updating items...")
                                currentPlaylistJson = newPlaylistJson
                                dataStoreManager.savePlaylist(newPlaylistJson)
                                startCaching(newPlaylist)
                            } else {
                                Log.d(TAG, "😴 Timeline unchanged.")
                                if (_appState.value is AppState.LicenseExpired || _appState.value is AppState.Error) {
                                    Log.d(TAG, "🎉 Recovering from error state...")
                                    _appState.value = AppState.Playing(newPlaylist, cacheManager.getCacheProgress(newPlaylist.items))
                                }
                            }
                        }
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "❌ Timeline API failed: ${error.message}")
                    if (error is TimelineLicenseExpiredException) {
                        Log.w(TAG, "🚫 License expired error from Timeline API")
                        error.response?.let { resp ->
                            val expiresAt = extractLicenseFromTimeline(resp)
                            if (!expiresAt.isNullOrEmpty()) {
                                lastLicenseExpiry = parseDate(expiresAt)
                                _licenseExpiryDate.value = expiresAt
                                dataStoreManager.saveLicenseExpiry(expiresAt)
                            }
                        }
                        _appState.value = AppState.LicenseExpired
                    } else if (isLicenseExpired()) {
                        _appState.value = AppState.LicenseExpired
                    }
                }
        }
    }

    fun register(code: String) {
        viewModelScope.launch {
            Log.d(TAG, "🚀 Starting new registration for: $code")
            _appState.value = AppState.Loading
            repository.registerDevice(code, deviceUid)
                .onSuccess { response ->
                    Log.d(TAG, "✅ Registration API success")
                    val expiresAt = extractLicense(response)
                    if (!expiresAt.isNullOrEmpty()) {
                        lastLicenseExpiry = parseDate(expiresAt)
                        _licenseExpiryDate.value = expiresAt
                        dataStoreManager.saveLicenseExpiry(expiresAt)
                    }

                    if (isLicenseExpired()) {
                        Log.w(TAG, "🚫 License expired immediately after registration")
                        _appState.value = AppState.LicenseExpired
                        return@onSuccess
                    }

                    if (response.success && response.data?.playlist != null) {
                        Log.d(TAG, "🎉 Registration complete, starting playback...")
                        val playlist = response.data.playlist
                        val newPlaylistJson = gson.toJson(playlist)
                        currentPlaylistJson = newPlaylistJson
                        dataStoreManager.savePlaylistCode(code)
                        dataStoreManager.savePlaylistId(playlist.id)
                        dataStoreManager.saveDeviceUid(deviceUid)
                        dataStoreManager.savePlaylist(newPlaylistJson)
                        startCaching(playlist)
                    } else {
                        Log.e(TAG, "❌ Registration failed with message: ${response.message}")
                        _appState.value = AppState.Error(response.message ?: "Unknown error")
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "❌ Registration API failed: ${error.message}")
                    if (error is LicenseExpiredException) {
                        error.response?.let { resp ->
                            val expiresAt = extractLicense(resp)
                            if (!expiresAt.isNullOrEmpty()) {
                                lastLicenseExpiry = parseDate(expiresAt)
                                _licenseExpiryDate.value = expiresAt
                                dataStoreManager.saveLicenseExpiry(expiresAt)
                            }
                        }
                        _appState.value = AppState.LicenseExpired
                        return@onFailure
                    }
                    val savedPlaylistJson = dataStoreManager.savedPlaylist.first()
                    if (isLicenseExpired()) {
                        _appState.value = AppState.LicenseExpired
                        return@onFailure
                    }
                    if (!savedPlaylistJson.isNullOrEmpty()) {
                        Log.d(TAG, "📡 Offline: Falling back to saved playlist...")
                        try {
                            val playlist = gson.fromJson(savedPlaylistJson, Playlist::class.java)
                            _appState.value = AppState.Playing(playlist, cacheManager.getCacheProgress(playlist.items))
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Offline fallback failed: JSON parse error")
                            _appState.value = AppState.Error(error.message ?: "Unknown error")
                        }
                    } else {
                        _appState.value = AppState.Error(error.message ?: "Unknown error")
                    }
                }
        }
    }

    private fun startCaching(playlist: Playlist) {
        viewModelScope.launch {
            _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
            _appState.value = AppState.Playing(playlist, _cacheProgress.value)
            
            playlist.items.forEach { item ->
                cacheManager.downloadMedia(item, baseUrl)
                _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
                _appState.value = AppState.Playing(playlist, _cacheProgress.value)
            }
        }
    }

    fun resetRegistration() {
        viewModelScope.launch {
            dataStoreManager.savePlaylistCode("")
            dataStoreManager.savePlaylistId("")
            dataStoreManager.savePlaylist("")
            dataStoreManager.saveLicenseExpiry("")
            lastLicenseExpiry = null
            _licenseExpiryDate.value = null
            _appState.value = AppState.RegistrationRequired
        }
    }
}
