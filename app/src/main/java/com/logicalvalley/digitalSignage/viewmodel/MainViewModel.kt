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
import kotlinx.coroutines.Job

import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

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

    private val _failedDownloads = MutableStateFlow<Set<String>>(emptySet())
    val failedDownloads: StateFlow<Set<String>> = _failedDownloads.asStateFlow()

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()

    private val _storageStats = MutableStateFlow<MediaCacheManager.StorageStats?>(null)
    val storageStats: StateFlow<MediaCacheManager.StorageStats?> = _storageStats.asStateFlow()

    private val _currentDownloadProgress = MutableStateFlow<MediaCacheManager.DownloadProgress?>(null)
    val currentDownloadProgress: StateFlow<MediaCacheManager.DownloadProgress?> = _currentDownloadProgress.asStateFlow()
    
    data class OverallDownloadStats(
        val totalItems: Int,
        val completedItems: Int,
        val currentlyDownloading: String?,
        val currentProgress: Int,
        val totalBytesDownloaded: Long,
        val totalBytesRequired: Long
    ) {
        fun getOverallPercentage(): Int {
            return if (totalBytesRequired > 0) {
                ((totalBytesDownloaded * 100) / totalBytesRequired).toInt()
            } else {
                (completedItems * 100) / totalItems.coerceAtLeast(1)
            }
        }
        
        fun getProgressText(): String {
            val downloadedMB = totalBytesDownloaded / 1024 / 1024
            val totalMB = totalBytesRequired / 1024 / 1024
            return "$completedItems/$totalItems items (${getOverallPercentage()}%) - $downloadedMB / $totalMB MB"
        }
    }
    
    // Individual video download progress
    data class VideoDownloadProgress(
        val itemId: String,
        val fileName: String,
        val fileSize: Long,
        val downloadedBytes: Long
    ) {
        fun getProgress(): Float {
            return if (fileSize > 0) {
                (downloadedBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
            } else 0f
        }
    }
    
    private val _overallDownloadStats = MutableStateFlow<OverallDownloadStats?>(null)
    val overallDownloadStats: StateFlow<OverallDownloadStats?> = _overallDownloadStats.asStateFlow()
    
    private val _videoProgressList = MutableStateFlow<List<VideoDownloadProgress>>(emptyList())
    val videoProgressList: StateFlow<List<VideoDownloadProgress>> = _videoProgressList.asStateFlow()
    
    private val _isNetworkConnected = MutableStateFlow(false)
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private var currentPlaylistJson: String? = null
    private var lastLicenseExpiry: Date? = null
    private var isCaching = false
    private var cachedItemIds = mutableSetOf<String>()
    private var qrExpiryTime: Date? = null
    private var cachingJob: Job? = null
    private var currentPlaylist: Playlist? = null
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        // Reset caching flag on app start (coroutines don't survive app restart)
        isCaching = false
        
        socketManager.connect(onStatusChange = { connected ->
            _isSocketConnected.value = connected
        })
        setupSocketListeners()
        setupNetworkMonitoring()
        loadFailedDownloads()
        checkRegistration()
        startPeriodicCheck()
    }
    
    private fun setupNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "🌐 Network connected")
                val wasDisconnected = !_isNetworkConnected.value
                _isNetworkConnected.value = true
                
                // Auto-resume downloads if they were interrupted
                if (wasDisconnected) {
                    Log.d(TAG, "🔄 Network reconnected - checking for pending downloads")
                    viewModelScope.launch {
                        delay(2000) // Wait for network to stabilize
                        autoResumeDownloads()
                    }
                }
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "📡 Network disconnected")
                _isNetworkConnected.value = false
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            // Check initial state
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            _isNetworkConnected.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register network callback", e)
        }
    }
    
    private fun autoResumeDownloads() {
        currentPlaylist?.let { playlist ->
            val progress = cacheManager.getCacheProgress(playlist.items)
            if (progress < 1.0f && !isCaching) {
                Log.d(TAG, "🔄 Auto-resuming downloads (progress: ${(progress * 100).toInt()}%)")
                startCaching(playlist)
            }
        }
    }
    
    private fun loadFailedDownloads() {
        viewModelScope.launch {
            dataStoreManager.failedDownloads.collect { savedFailedIds ->
                if (savedFailedIds.isNotEmpty()) {
                    _failedDownloads.value = savedFailedIds
                    Log.d(TAG, "📥 Restored ${savedFailedIds.size} failed downloads from storage")
                }
            }
        }
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
        
        Log.d(TAG, "🔍 Parsing date string: '$cleanedDate'")
        
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US).apply {
                    if (format.contains("Z") && !format.contains("X")) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                }
                val parsed = sdf.parse(cleanedDate)
                Log.d(TAG, "✅ Successfully parsed with format: $format → $parsed")
                return parsed
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Format '$format' failed: ${e.message}")
            }
        }
        
        Log.e(TAG, "❌ All date formats failed for: '$cleanedDate'")
        return null
    }

    private fun checkRegistration() {
        Log.d(TAG, "🔍 STARTUP: Checking registration status...")
        viewModelScope.launch {
            Log.d(TAG, "⏳ Starting 2-second splash delay...")
            // Show loading/splash for at least 2 seconds
            delay(2000)
            Log.d(TAG, "⌛ Splash delay complete. Processing registration...")
            
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
                        currentPlaylist = playlist
                        
                        // Initialize video progress list on app restart
                        initializeVideoProgressList(playlist)
                        
                        // Check if caching is incomplete and resume if needed
                        val cacheProgress = cacheManager.getCacheProgress(playlist.items)
                        _appState.value = AppState.Playing(playlist, cacheProgress)
                        
                        if (cacheProgress < 1.0f) {
                            Log.d(TAG, "🔄 Incomplete cache detected (${(cacheProgress * 100).toInt()}%). Resuming downloads...")
                            startCaching(playlist)
                        }
                        
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
                        Log.d(TAG, "🔑 Session Token: ${response.data.sessionToken}")
                        Log.d(TAG, "⏰ QR Expires at (raw): ${response.data.expiresAt}")
                        
                        // Store expiry time for periodic checking
                        qrExpiryTime = parseDate(response.data.expiresAt)
                        if (qrExpiryTime != null) {
                            val now = Date()
                            val expiryMillis = qrExpiryTime!!.time
                            val nowMillis = now.time
                            val diffMillis = expiryMillis - nowMillis
                            val minutesUntilExpiry = diffMillis / 1000 / 60
                            val secondsUntilExpiry = diffMillis / 1000
                            
                            Log.d(TAG, "⏰ Parsed expiry: $qrExpiryTime")
                            Log.d(TAG, "⏰ Current time: $now")
                            Log.d(TAG, "⏰ Expiry millis: $expiryMillis")
                            Log.d(TAG, "⏰ Now millis: $nowMillis")
                            Log.d(TAG, "⏰ Diff millis: $diffMillis")
                            Log.d(TAG, "⏰ QR will expire in $minutesUntilExpiry minutes ($secondsUntilExpiry seconds)")
                            
                            // Safety check: If expiry is more than 10 minutes in the future, something is wrong
                            if (minutesUntilExpiry > 10) {
                                Log.e(TAG, "❌ WARNING: QR expiry time seems wrong! Expected ~5 minutes, got $minutesUntilExpiry minutes")
                                Log.e(TAG, "❌ This is likely a timezone or date parsing issue")
                                Log.e(TAG, "❌ Forcing QR to expire in 4 minutes as fallback")
                                qrExpiryTime = Date(nowMillis + (4 * 60 * 1000))
                                Log.d(TAG, "✅ Corrected expiry: $qrExpiryTime (4 minutes from now)")
                            }
                        } else {
                            Log.e(TAG, "❌ Could not parse QR expiry time: ${response.data.expiresAt}")
                            // Fallback: Set expiry to 4 minutes from now
                            qrExpiryTime = Date(Date().time + (4 * 60 * 1000))
                            Log.w(TAG, "⚠️ Using fallback expiry: $qrExpiryTime (4 minutes from now)")
                        }
                        
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
                
                // Check if QR needs refresh (on registration screen)
                if (_appState.value is AppState.RegistrationRequired) {
                    val expiry = qrExpiryTime
                    if (expiry != null) {
                        val now = Date()
                        val secondsUntilExpiry = (expiry.time - now.time) / 1000
                        
                        // Refresh 90 seconds before expiry (enough buffer for periodic check + network delay)
                        // Backend expires sessions in 5 minutes, so this refreshes at ~3.5 minutes
                        if (secondsUntilExpiry <= 90) {
                            Log.d(TAG, "🔄 QR expires in $secondsUntilExpiry seconds - auto-refreshing now!")
                            initQrRegistration()
                        } else {
                            Log.d(TAG, "✅ QR still valid - expires in ${secondsUntilExpiry / 60} minutes")
                        }
                    } else {
                        Log.d(TAG, "⚠️ On registration screen but no QR expiry time set")
                    }
                }
                
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
                        
                        // Initialize video progress list for new playlist
                        initializeVideoProgressList(newPlaylist)
                        
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
            
            // Clear QR expiry time since registration is complete
            qrExpiryTime = null
            
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
        // Check if caching job is actually running
        if (isCaching && cachingJob?.isActive == true) {
            Log.d(TAG, "⏩ Caching already in progress, skipping...")
            // Update stats even if caching is in progress (for app restart scenario)
            updateDownloadStatsForInProgress(playlist)
            return
        }
        
        // If flag was set but job is not active, reset and continue
        if (isCaching && cachingJob?.isActive == false) {
            Log.w(TAG, "⚠️ Caching flag was set but job is not active. Resetting and continuing...")
            isCaching = false
            cachingJob = null
        }
        
        cachingJob = viewModelScope.launch {
            isCaching = true
            currentPlaylist = playlist  // Store for auto-resume
            Log.d(TAG, "🔽 Starting media caching for ${playlist.items.size} items...")
            
            // Get initial storage stats
            _storageStats.value = cacheManager.getStorageStats()
            Log.d(TAG, "💾 Storage before caching: ${_storageStats.value?.availableBytes?.div(1024)?.div(1024)} MB available")
            
            // Clear orphaned cache files from previous playlists
            Log.d(TAG, "🧹 Cleaning orphaned cache files...")
            cacheManager.clearOrphanedCache(playlist.items)
            
            // Update storage stats after cleanup
            _storageStats.value = cacheManager.getStorageStats()
            Log.d(TAG, "💾 Storage after cleanup: ${_storageStats.value?.availableBytes?.div(1024)?.div(1024)} MB available")
            
            // Update progress immediately
            _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
            _appState.value = AppState.Playing(playlist, _cacheProgress.value)
            
            var successCount = 0
            var failureCount = 0
            var skippedCount = 0
            var totalBytesDownloaded = 0L
            val totalBytesRequired = playlist.items.sumOf { it.video?.fileSize ?: 0L }
            
            // Initialize video progress list with all items
            val initialProgressList = playlist.items.map { item ->
                val fileSize = item.video?.fileSize ?: 0L
                val downloadedBytes = if (cacheManager.getLocalFile(item) != null) fileSize else 0L
                if (downloadedBytes > 0) {
                    totalBytesDownloaded += downloadedBytes
                }
                VideoDownloadProgress(
                    itemId = item.id,
                    fileName = item.video?.fileName ?: "Unknown",
                    fileSize = fileSize,
                    downloadedBytes = downloadedBytes
                )
            }
            _videoProgressList.value = initialProgressList
            
            // Initialize overall stats with current state
            val currentCachedCount = (cacheManager.getCacheProgress(playlist.items) * playlist.items.size).toInt()
            _overallDownloadStats.value = OverallDownloadStats(
                totalItems = playlist.items.size,
                completedItems = currentCachedCount,
                currentlyDownloading = null,
                currentProgress = 0,
                totalBytesDownloaded = totalBytesDownloaded,
                totalBytesRequired = totalBytesRequired
            )
            
            Log.d(TAG, "📊 Initial stats: $currentCachedCount/${playlist.items.size} cached, ${totalBytesDownloaded / 1024 / 1024}/${totalBytesRequired / 1024 / 1024} MB")
            
            playlist.items.forEachIndexed { index, item ->
                val fileName = item.video?.fileName ?: "Unknown"
                val itemId = item.id
                
                // Skip if already successfully cached
                if (cachedItemIds.contains(itemId)) {
                    skippedCount++
                    Log.d(TAG, "⏭️ Already cached (${index + 1}/${playlist.items.size}): $fileName")
                    return@forEachIndexed
                }
                
                // Check if file exists before attempting download
                if (cacheManager.getLocalFile(item) != null) {
                    cachedItemIds.add(itemId)
                    skippedCount++
                    totalBytesDownloaded += (item.video?.fileSize ?: 0L)
                    Log.d(TAG, "⏭️ File exists (${index + 1}/${playlist.items.size}): $fileName")
                    
                    // Update overall stats for already cached items
                    _overallDownloadStats.value = OverallDownloadStats(
                        totalItems = playlist.items.size,
                        completedItems = successCount + skippedCount,
                        currentlyDownloading = null,
                        currentProgress = 0,
                        totalBytesDownloaded = totalBytesDownloaded,
                        totalBytesRequired = totalBytesRequired
                    )
                    
                    return@forEachIndexed
                }
                
                Log.d(TAG, "📥 Caching item ${index + 1}/${playlist.items.size}: $fileName")
                
                // Update overall stats - starting download
                _overallDownloadStats.value = OverallDownloadStats(
                    totalItems = playlist.items.size,
                    completedItems = successCount + skippedCount,
                    currentlyDownloading = fileName,
                    currentProgress = 0,
                    totalBytesDownloaded = totalBytesDownloaded,
                    totalBytesRequired = totalBytesRequired
                )
                
                val success = try {
                    cacheManager.downloadMedia(item, baseUrl) { progress ->
                        // Update current file progress
                        _currentDownloadProgress.value = progress
                        
                        // Update overall stats with current download progress
                        _overallDownloadStats.value = OverallDownloadStats(
                            totalItems = playlist.items.size,
                            completedItems = successCount + skippedCount,
                            currentlyDownloading = fileName,
                            currentProgress = progress.percentage,
                            totalBytesDownloaded = totalBytesDownloaded + progress.downloadedBytes,
                            totalBytesRequired = totalBytesRequired
                        )
                        
                        // Update video progress list
                        _videoProgressList.value = _videoProgressList.value.map { videoProgress ->
                            if (videoProgress.itemId == itemId) {
                                videoProgress.copy(downloadedBytes = progress.downloadedBytes)
                            } else {
                                videoProgress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception caching $fileName", e)
                    false
                }
                
                if (success) {
                    successCount++
                    cachedItemIds.add(itemId)
                    totalBytesDownloaded += (item.video?.fileSize ?: 0L)
                    
                    // Update video progress list - mark as complete
                    _videoProgressList.value = _videoProgressList.value.map { videoProgress ->
                        if (videoProgress.itemId == itemId) {
                            videoProgress.copy(downloadedBytes = videoProgress.fileSize)
                        } else {
                            videoProgress
                        }
                    }
                    
                    // Remove from failed list if it was there
                    _failedDownloads.value = _failedDownloads.value - itemId
                    // Persist failed downloads
                    viewModelScope.launch {
                        dataStoreManager.saveFailedDownloads(_failedDownloads.value)
                    }
                    Log.d(TAG, "✅ Successfully cached: $fileName")
                } else {
                    failureCount++
                    // Add to failed list
                    _failedDownloads.value = _failedDownloads.value + itemId
                    // Persist failed downloads
                    viewModelScope.launch {
                        dataStoreManager.saveFailedDownloads(_failedDownloads.value)
                    }
                    Log.e(TAG, "❌ Failed to cache: $fileName (itemId: $itemId)")
                }
                
                // Clear current download progress
                _currentDownloadProgress.value = null
                
                // Update progress after each item
                _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
                _appState.value = AppState.Playing(playlist, _cacheProgress.value)
                
                // Update overall stats after item completes
                _overallDownloadStats.value = OverallDownloadStats(
                    totalItems = playlist.items.size,
                    completedItems = successCount + skippedCount,
                    currentlyDownloading = null,
                    currentProgress = 0,
                    totalBytesDownloaded = totalBytesDownloaded,
                    totalBytesRequired = totalBytesRequired
                )
            }
            
            isCaching = false
            cachingJob = null
            
            // Update final storage stats
            _storageStats.value = cacheManager.getStorageStats()
            
            Log.d(TAG, "🏁 Caching complete: $successCount downloaded, $skippedCount already cached, $failureCount failed")
            Log.d(TAG, "💾 Final storage: ${_storageStats.value?.availableBytes?.div(1024)?.div(1024)} MB available")
            Log.d(TAG, "📦 Cache size: ${_storageStats.value?.cacheBytes?.div(1024)?.div(1024)} MB")
            
            if (failureCount > 0) {
                Log.w(TAG, "⚠️ Some downloads failed. Failed items: ${_failedDownloads.value}")
            }
            
            // Keep final stats for 5 seconds before clearing (so UI can display completion)
            delay(5000)
            _currentDownloadProgress.value = null
            _overallDownloadStats.value = null
        }
    }

    fun retryFailedDownloads() {
        viewModelScope.launch {
            val currentState = _appState.value
            if (currentState !is AppState.Playing) {
                Log.w(TAG, "⚠️ Cannot retry downloads - not in Playing state")
                return@launch
            }

            val failedItemIds = _failedDownloads.value
            if (failedItemIds.isEmpty()) {
                Log.d(TAG, "ℹ️ No failed downloads to retry")
                return@launch
            }

            if (_isRetrying.value) {
                Log.w(TAG, "⚠️ Retry already in progress")
                return@launch
            }

            _isRetrying.value = true
            Log.d(TAG, "🔄 Starting retry for ${failedItemIds.size} failed downloads...")

            val playlist = currentState.playlist
            val failedItems = playlist.items.filter { failedItemIds.contains(it.id) }
            val totalBytesRequired = failedItems.sumOf { it.video?.fileSize ?: 0L }

            var retrySuccessCount = 0
            var retryFailCount = 0
            var totalBytesDownloaded = 0L
            
            // Initialize overall stats for retry
            _overallDownloadStats.value = OverallDownloadStats(
                totalItems = failedItems.size,
                completedItems = 0,
                currentlyDownloading = null,
                currentProgress = 0,
                totalBytesDownloaded = 0L,
                totalBytesRequired = totalBytesRequired
            )

            failedItems.forEachIndexed { index, item ->
                val fileName = item.video?.fileName ?: "Unknown"
                Log.d(TAG, "🔄 Retrying (${index + 1}/${failedItems.size}): $fileName")

                // Update overall stats - starting retry
                _overallDownloadStats.value = OverallDownloadStats(
                    totalItems = failedItems.size,
                    completedItems = retrySuccessCount,
                    currentlyDownloading = fileName,
                    currentProgress = 0,
                    totalBytesDownloaded = totalBytesDownloaded,
                    totalBytesRequired = totalBytesRequired
                )

                val success = try {
                    cacheManager.downloadMedia(item, baseUrl) { progress ->
                        // Update current file progress
                        _currentDownloadProgress.value = progress
                        
                        // Update overall stats with current download progress
                        _overallDownloadStats.value = OverallDownloadStats(
                            totalItems = failedItems.size,
                            completedItems = retrySuccessCount,
                            currentlyDownloading = fileName,
                            currentProgress = progress.percentage,
                            totalBytesDownloaded = totalBytesDownloaded + progress.downloadedBytes,
                            totalBytesRequired = totalBytesRequired
                        )
                        
                        // Update video progress list
                        _videoProgressList.value = _videoProgressList.value.map { videoProgress ->
                            if (videoProgress.itemId == item.id) {
                                videoProgress.copy(downloadedBytes = progress.downloadedBytes)
                            } else {
                                videoProgress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during retry for $fileName", e)
                    false
                }

                if (success) {
                    retrySuccessCount++
                    cachedItemIds.add(item.id)
                    totalBytesDownloaded += (item.video?.fileSize ?: 0L)
                    
                    // Update video progress list - mark as complete
                    _videoProgressList.value = _videoProgressList.value.map { videoProgress ->
                        if (videoProgress.itemId == item.id) {
                            videoProgress.copy(downloadedBytes = videoProgress.fileSize)
                        } else {
                            videoProgress
                        }
                    }
                    
                    _failedDownloads.value = _failedDownloads.value - item.id
                    // Persist failed downloads
                    viewModelScope.launch {
                        dataStoreManager.saveFailedDownloads(_failedDownloads.value)
                    }
                    Log.d(TAG, "✅ Retry successful: $fileName")
                } else {
                    retryFailCount++
                    Log.e(TAG, "❌ Retry failed: $fileName")
                }

                // Clear current download progress
                _currentDownloadProgress.value = null

                // Update progress after each retry
                _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
                _appState.value = AppState.Playing(playlist, _cacheProgress.value)
                
                // Update overall stats after retry completes
                _overallDownloadStats.value = OverallDownloadStats(
                    totalItems = failedItems.size,
                    completedItems = retrySuccessCount,
                    currentlyDownloading = null,
                    currentProgress = 0,
                    totalBytesDownloaded = totalBytesDownloaded,
                    totalBytesRequired = totalBytesRequired
                )
            }

            _isRetrying.value = false
            
            // Update storage stats after retry
            _storageStats.value = cacheManager.getStorageStats()
            
            Log.d(TAG, "🏁 Retry complete: $retrySuccessCount succeeded, $retryFailCount still failed")
            Log.d(TAG, "💾 Storage after retry: ${_storageStats.value?.availableBytes?.div(1024)?.div(1024)} MB available")
            
            // Keep final stats for 5 seconds before clearing
            delay(5000)
            _currentDownloadProgress.value = null
            _overallDownloadStats.value = null
            
            if (retryFailCount > 0) {
                Log.w(TAG, "⚠️ Still have ${_failedDownloads.value.size} failed downloads")
            } else {
                Log.d(TAG, "🎉 All downloads successful!")
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
            // Clear QR expiry time
            qrExpiryTime = null
            
            // Force state to Loading to ensure UI switches and QR init isn't blocked
            _appState.value = AppState.Loading
            
            // Clear caching state
            isCaching = false
            cachedItemIds.clear()
            _failedDownloads.value = emptySet()
            _isRetrying.value = false
            
            // Clear persisted failed downloads
            dataStoreManager.saveFailedDownloads(emptySet())
            
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
    
    /**
     * Update download stats for in-progress downloads (e.g., after app restart)
     */
    private fun updateDownloadStatsForInProgress(playlist: Playlist) {
        viewModelScope.launch {
            val totalBytesRequired = playlist.items.sumOf { it.video?.fileSize ?: 0L }
            var totalBytesDownloaded = 0L
            
            // Calculate already cached bytes
            playlist.items.forEach { item ->
                if (cacheManager.getLocalFile(item) != null) {
                    totalBytesDownloaded += (item.video?.fileSize ?: 0L)
                }
            }
            
            val currentCachedCount = (cacheManager.getCacheProgress(playlist.items) * playlist.items.size).toInt()
            
            // Update overall stats to show current progress
            _overallDownloadStats.value = OverallDownloadStats(
                totalItems = playlist.items.size,
                completedItems = currentCachedCount,
                currentlyDownloading = "Downloading in background...",
                currentProgress = 0,
                totalBytesDownloaded = totalBytesDownloaded,
                totalBytesRequired = totalBytesRequired
            )
            
            Log.d(TAG, "📊 Updated stats for in-progress: $currentCachedCount/${playlist.items.size} cached, ${totalBytesDownloaded / 1024 / 1024}/${totalBytesRequired / 1024 / 1024} MB")
        }
    }
    
    /**
     * Initialize video progress list based on currently cached files
     */
    private fun initializeVideoProgressList(playlist: Playlist) {
        val progressList = playlist.items.map { item ->
            val fileSize = item.video?.fileSize ?: 0L
            val downloadedBytes = if (cacheManager.getLocalFile(item) != null) fileSize else 0L
            VideoDownloadProgress(
                itemId = item.id,
                fileName = item.video?.fileName ?: "Unknown",
                fileSize = fileSize,
                downloadedBytes = downloadedBytes
            )
        }
        _videoProgressList.value = progressList
        Log.d(TAG, "📊 Initialized video progress list with ${progressList.size} items")
    }
}
