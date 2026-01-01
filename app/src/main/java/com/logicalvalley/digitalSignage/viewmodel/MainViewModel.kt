package com.logicalvalley.digitalSignage.viewmodel

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.logicalvalley.digitalSignage.config.AppConfig
import com.logicalvalley.digitalSignage.data.api.RetrofitClient
import com.logicalvalley.digitalSignage.data.api.SocketManager
import com.logicalvalley.digitalSignage.data.local.DataStoreManager
import com.logicalvalley.digitalSignage.data.local.MediaCacheManager
import com.logicalvalley.digitalSignage.data.model.*
import com.logicalvalley.digitalSignage.data.repository.LicenseExpiredException
import com.logicalvalley.digitalSignage.data.repository.TimelineLicenseExpiredException
import com.logicalvalley.digitalSignage.data.repository.DeviceDeregisteredException
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
    data class RegistrationRequired(val qrData: InitRegistrationData? = null, val error: String? = null) : AppState()
    object LicenseExpired : AppState()
    data class Playing(val playlist: Playlist, val cacheProgress: Float) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val repository = PlaylistRepository(RetrofitClient.apiService)
    private val dataStoreManager = DataStoreManager(application)
    private val cacheManager = MediaCacheManager(application)
    private val socketManager = SocketManager()
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

    private val _isSocketConnected = MutableStateFlow(false)
    val isSocketConnected: StateFlow<Boolean> = _isSocketConnected.asStateFlow()

    private val _remoteCommand = MutableStateFlow<String?>(null)
    val remoteCommand: StateFlow<String?> = _remoteCommand.asStateFlow()

    private var currentPlaylistJson: String? = null
    private var lastLicenseExpiry: Date? = null

    init {
        socketManager.connect(onStatusChange = { connected ->
            _isSocketConnected.value = connected
        })
        setupSocketListeners()
        checkRegistration()
        startPeriodicCheck()
    }

    private fun setupSocketListeners() {
        socketManager.onRegistrationComplete { response ->
            Log.d(TAG, "🔔 Socket: Registration complete event received")
            handleRegistrationSuccess(response, response.data?.playlist?.code ?: "")
        }
        socketManager.onRemoteCommand(
            onFullscreenEnter = { _remoteCommand.value = "ENTER_FULLSCREEN" },
            onFullscreenExit = { _remoteCommand.value = "EXIT_FULLSCREEN" },
            onForceDeregister = { 
                Log.w(TAG, "🚫 Socket: Force deregister event received")
                resetRegistration() 
            }
        )
    }

    fun clearRemoteCommand() {
        _remoteCommand.value = null
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.disconnect()
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
        Log.d(TAG, "🔍 STARTUP: Checking registration status...")
        viewModelScope.launch {
            // Show loading/splash for at least 2 seconds
            delay(2000)
            
            val savedCode = dataStoreManager.playlistCode.first() ?: ""
            val savedPlaylistId = dataStoreManager.playlistId.first() ?: ""
            val savedPlaylistJson = dataStoreManager.savedPlaylist.first() ?: ""
            val savedLicenseExpiry = dataStoreManager.licenseExpiry.first() ?: ""
            
            Log.d(TAG, "📂 DATASTORE LOADED:")
            Log.d(TAG, "   - Playlist ID: '$savedPlaylistId'")
            Log.d(TAG, "   - Playlist Code: '$savedCode'")
            Log.d(TAG, "   - Has Cached JSON: ${savedPlaylistJson.isNotEmpty()}")
            Log.d(TAG, "   - License Expiry: '$savedLicenseExpiry'")

            if (savedLicenseExpiry.isNotEmpty() && savedLicenseExpiry != "null") {
                _licenseExpiryDate.value = savedLicenseExpiry
                lastLicenseExpiry = parseDate(savedLicenseExpiry)
            }

            if (savedPlaylistId.isNotEmpty()) {
                Log.d(TAG, "✅ Device is registered with ID: $savedPlaylistId")
                
                if (isLicenseExpired()) {
                    Log.w(TAG, "⚠️ License expired, blocking playback")
                    _appState.value = AppState.LicenseExpired
                    refreshTimeline(savedPlaylistId)
                    return@launch
                }

                if (savedPlaylistJson.isNotEmpty()) {
                    try {
                        Log.d(TAG, "▶️ Starting playback from cache...")
                        val playlist = gson.fromJson(savedPlaylistJson, Playlist::class.java)
                        currentPlaylistJson = savedPlaylistJson
                        _appState.value = AppState.Playing(playlist, cacheManager.getCacheProgress(playlist.items))
                        socketManager.connectPlayer(deviceUid, savedPlaylistId)
                        refreshTimeline(savedPlaylistId)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Cache parse error, falling back to timeline", e)
                        socketManager.connectPlayer(deviceUid, savedPlaylistId)
                        refreshTimeline(savedPlaylistId)
                    }
                } else {
                    Log.d(TAG, "❓ No cached JSON, fetching from server...")
                    socketManager.connectPlayer(deviceUid, savedPlaylistId)
                    refreshTimeline(savedPlaylistId)
                }
            } else if (savedCode.isNotEmpty()) {
                Log.d(TAG, "🔄 Legacy code registration found: $savedCode")
                register(savedCode)
            } else {
                Log.d(TAG, "👋 No registration found. Proceeding to QR flow.")
                initQrRegistration()
            }
        }
    }

    fun initQrRegistration() {
        viewModelScope.launch {
            // Safety: Never show QR if we are already playing or have a saved ID
            if (_appState.value is AppState.Playing) {
                Log.d(TAG, "🚫 Skipping QR init: App is already playing")
                return@launch
            }

            Log.d(TAG, "🚀 Fetching QR Registration Session...")
            // Don't set full-screen loading if we already have the screen showing
            if (_appState.value !is AppState.RegistrationRequired) {
                _appState.value = AppState.Loading
            }
            
            repository.initRegistration(deviceUid)
                .onSuccess { response ->
                    if (response.success && response.data != null) {
                        Log.d(TAG, "✅ QR Session fetched successfully")
                        Log.d(TAG, "🔗 QR URL: ${response.data.registrationUrl}")
                        socketManager.joinDeviceRoom(deviceUid)
                        _appState.value = AppState.RegistrationRequired(response.data)
                    } else {
                        Log.e(TAG, "❌ QR Session success=false or data=null: ${response.message}")
                        _appState.value = AppState.RegistrationRequired(null, "Server returned error: ${response.message}")
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "❌ Failed to fetch QR Session", error)
                    _appState.value = AppState.RegistrationRequired(null, "Connection failed: ${error.message}. Is AppConfig.BASE_URL correct? (${AppConfig.BASE_URL})")
                }
        }
    }

    private fun startPeriodicCheck() {
        viewModelScope.launch {
            while (true) {
                delay(30000) // 30 seconds
                Log.d(TAG, "⏰ Periodic check triggered...")
                
                // Send socket ping if playing
                if (_appState.value is AppState.Playing) {
                    socketManager.sendPing(deviceUid)
                }

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
            Log.w(TAG, "⚠️ License object missing from this response. This is expected for Socket events. Will fetch via timeline.")
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
            repository.getPlaylistTimeline(playlistId, deviceUid)
                .onSuccess { response ->
                    Log.d(TAG, "✅ Timeline API success")
                    
                    if (response.deviceDeleted == true) {
                        Log.w(TAG, "🚫 Device deleted flag in timeline! Resetting...")
                        resetRegistration()
                        return@onSuccess
                    }

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
                        Log.d(TAG, "📋 Timeline items received: ${response.items.size}")
                        
                        // 1. Get or create base playlist
                        val savedPlaylistJson = dataStoreManager.savedPlaylist.first()
                        val currentPlaylist = if (!savedPlaylistJson.isNullOrEmpty()) {
                            gson.fromJson(savedPlaylistJson, Playlist::class.java)
                        } else {
                            // If we have no cached JSON, create a dummy one to hold the items
                            Playlist(id = playlistId, name = "My Playlist", code = "", items = response.items)
                        }

                        val newPlaylist = currentPlaylist.copy(items = response.items)
                        val newPlaylistJson = gson.toJson(newPlaylist)
                        
                        // 2. Update state if needed (always update if Loading)
                        if (newPlaylistJson != currentPlaylistJson || _appState.value is AppState.Loading) {
                            Log.d(TAG, "🆕 Updating items and transitioning to Playing state")
                            currentPlaylistJson = newPlaylistJson
                            dataStoreManager.savePlaylist(newPlaylistJson)
                            startCaching(newPlaylist)
                        } else {
                            Log.d(TAG, "😴 Timeline unchanged.")
                            if (_appState.value is AppState.LicenseExpired || _appState.value is AppState.Error) {
                                Log.d(TAG, "🎉 Recovering from error/expired state...")
                                _appState.value = AppState.Playing(newPlaylist, cacheManager.getCacheProgress(newPlaylist.items))
                            }
                        }
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "❌ Timeline API failed: ${error.message}")
                    if (error is DeviceDeregisteredException) {
                        Log.w(TAG, "🚫 Device deregistered from backend")
                        resetRegistration()
                    } else if (error is TimelineLicenseExpiredException) {
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
                    handleRegistrationSuccess(response, code)
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

    private fun handleRegistrationSuccess(response: RegisterResponse, code: String) {
        viewModelScope.launch {
            Log.d(TAG, "✅ Registration Success Flow Started")
            
            // Extract playlist and device from either data wrapper or top level
            val playlist = response.data?.playlist ?: response.topLevelPlaylist
            val device = response.data?.device ?: response.topLevelDevice

            if (playlist == null) {
                Log.e(TAG, "❌ No playlist data in registration success response! Check both 'data.playlist' and 'playlist'")
                _appState.value = AppState.Error("Invalid response from server: Missing playlist")
                return@launch
            }

            val expiresAt = extractLicense(response)
            Log.d(TAG, "🎉 Registration complete, starting playback...")
            val newPlaylistJson = gson.toJson(playlist)
            currentPlaylistJson = newPlaylistJson
            dataStoreManager.savePlaylistCode(code)
            dataStoreManager.savePlaylistId(playlist.id)
            dataStoreManager.saveDeviceUid(deviceUid)
            dataStoreManager.savePlaylist(newPlaylistJson)
            
            // Connect to socket for real-time tracking
            socketManager.connectPlayer(deviceUid, playlist.id)
            
            // Set state to Playing immediately to switch screens
            _appState.value = AppState.Playing(playlist, cacheManager.getCacheProgress(playlist.items))
            
            // If license was missing, refresh timeline immediately to get it
            if (expiresAt == null) {
                Log.d(TAG, "🔄 License missing in registration, triggering immediate timeline refresh...")
                refreshTimeline(playlist.id)
            }
            
            startCaching(playlist)
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

    fun manualDeregister() {
        Log.d(TAG, "🗑️ Manual deregistration requested")
        // Fire and forget the server-side deregistration
        viewModelScope.launch {
            repository.deregisterDevice(deviceUid)
        }
        // Always clear local data immediately, even if offline
        resetRegistration()
    }

    fun resetRegistration() {
        viewModelScope.launch {
            Log.d(TAG, "🧹 Clearing local registration data...")
            // Force state to Loading to ensure UI switches and QR init isn't blocked
            _appState.value = AppState.Loading
            
            dataStoreManager.savePlaylistCode("")
            dataStoreManager.savePlaylistId("")
            dataStoreManager.savePlaylist("")
            dataStoreManager.saveLicenseExpiry("")
            lastLicenseExpiry = null
            _licenseExpiryDate.value = null
            
            // Wait a tiny bit for DataStore to commit
            delay(100)
            
            initQrRegistration()
        }
    }
}
