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
import java.io.File

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import com.logicalvalley.digitalSignage.worker.DownloadWorker
import androidx.work.WorkManager

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
    private val workManager = WorkManager.getInstance(application)

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
        val downloadedBytes: Long,
        val actualTotalBytes: Long = fileSize  // Actual size reported by network
    ) {
        fun getProgress(): Float {
            val total = if (actualTotalBytes > 0) actualTotalBytes else fileSize
            return if (total > 0) {
                (downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
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
                        
                        // 2. Update state if needed (always update if Loading)
                        if (newPlaylistJson != currentPlaylistJson || _appState.value is AppState.Loading) {
                            Log.d(TAG, "🆕 Updating items and transitioning to Playing state")
                            currentPlaylistJson = newPlaylistJson
                            dataStoreManager.savePlaylist(newPlaylistJson)
                            
                            // Initialize video progress list ONLY when playlist changes
                            initializeVideoProgressList(newPlaylist)
                            
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
        Log.d(TAG, "🚀 Starting background downloads via WorkManager")
        currentPlaylist = playlist
        
        // Initialize progress list
        initializeVideoProgressList(playlist)
        
        // Calculate initial cache progress
        _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
        _appState.value = AppState.Playing(playlist, _cacheProgress.value)

        playlist.items.forEach { item ->
            val videoId = item.video?.id ?: return@forEach
            val fileName = item.video?.fileName ?: "Unknown"
            val fileSize = item.video?.fileSize ?: 0L
            
            // Check if already cached
            if (cacheManager.getLocalFile(item) != null) {
                Log.d(TAG, "✅ Already cached, skipping WorkManager: $fileName")
                return@forEach
            }

            Log.d(TAG, "enqueueing download for: $fileName ($videoId)")

            val inputData = workDataOf(
                "videoId" to videoId,
                "fileName" to fileName,
                "fileSize" to fileSize,
                "itemId" to item.id,
                "baseUrl" to baseUrl
            )

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .addTag("download")
                .addTag("item_${item.id}")
                .build()

            workManager.enqueueUniqueWork(
                "download_${item.id}",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
        
        observeDownloadProgress()
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            try {
                workManager.getWorkInfosByTagFlow("download").collect { workInfos ->
                    val currentProgressList = _videoProgressList.value.toMutableList()
                    var hasChanges = false
                    var totalBytesDownloaded = 0L
                    var totalBytesRequired = 0L
                    var activeDownloads = 0
                    var currentDownloadName: String? = null

                    workInfos.forEach { workInfo ->
                        val tag = workInfo.tags.find { it.startsWith("item_") }
                        val itemId = tag?.removePrefix("item_") ?: return@forEach
                        
                        val index = currentProgressList.indexOfFirst { it.itemId == itemId }
                        if (index != -1) {
                            val currentItem = currentProgressList[index]
                            
                            when (workInfo.state) {
                                WorkInfo.State.RUNNING -> {
                                    val progress = workInfo.progress
                                    val downloaded = progress.getLong("downloadedBytes", 0L)
                                    val total = progress.getLong("totalBytes", 0L)
                                    
                                    if (downloaded > 0) {
                                        currentProgressList[index] = currentItem.copy(
                                            downloadedBytes = downloaded,
                                            actualTotalBytes = if (total > 0) total else currentItem.fileSize
                                        )
                                        hasChanges = true
                                        activeDownloads++
                                        currentDownloadName = currentItem.fileName
                                    }
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    if (currentItem.downloadedBytes < currentItem.fileSize) {
                                        currentProgressList[index] = currentItem.copy(
                                            downloadedBytes = currentItem.fileSize,
                                            actualTotalBytes = currentItem.fileSize
                                        )
                                        hasChanges = true
                                        
                                        if (_failedDownloads.value.contains(itemId)) {
                                            _failedDownloads.value -= itemId
                                            launch { dataStoreManager.saveFailedDownloads(_failedDownloads.value) }
                                        }
                                    }
                                }
                                WorkInfo.State.FAILED -> {
                                    if (!_failedDownloads.value.contains(itemId)) {
                                        _failedDownloads.value += itemId
                                        launch { dataStoreManager.saveFailedDownloads(_failedDownloads.value) }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    
                    if (hasChanges) {
                        _videoProgressList.value = currentProgressList
                        
                        currentPlaylist?.let { playlist ->
                            _cacheProgress.value = cacheManager.getCacheProgress(playlist.items)
                            if (_appState.value is AppState.Playing) {
                                _appState.value = AppState.Playing(playlist, _cacheProgress.value)
                            }
                        }
                        
                        totalBytesDownloaded = currentProgressList.sumOf { it.downloadedBytes }
                        totalBytesRequired = currentProgressList.sumOf { if (it.actualTotalBytes > 0) it.actualTotalBytes else it.fileSize }
                        val completedItems = currentProgressList.count { it.downloadedBytes >= it.fileSize && it.fileSize > 0 }
                        
                        _overallDownloadStats.value = OverallDownloadStats(
                            totalItems = currentProgressList.size,
                            completedItems = completedItems,
                            currentlyDownloading = currentDownloadName,
                            currentProgress = if (totalBytesRequired > 0) ((totalBytesDownloaded * 100) / totalBytesRequired).toInt() else 0,
                            totalBytesDownloaded = totalBytesDownloaded,
                            totalBytesRequired = totalBytesRequired
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing download progress", e)
            }
        }
    }


    fun retryFailedDownloads() {
        Log.d(TAG, "🔄 Retrying failed downloads via WorkManager")
        
        val failedItemIds = _failedDownloads.value
        if (failedItemIds.isEmpty()) {
            Log.d(TAG, "ℹ️ No failed downloads to retry")
            return
        }
        
        val playlist = currentPlaylist ?: return
        
        failedItemIds.forEach { itemId ->
            val item = playlist.items.find { it.id == itemId } ?: return@forEach
            val videoId = item.video?.id ?: return@forEach
            val fileName = item.video?.fileName ?: "Unknown"
            val fileSize = item.video?.fileSize ?: 0L
            
            Log.d(TAG, "enqueueing retry for: $fileName ($videoId)")

            val inputData = workDataOf(
                "videoId" to videoId,
                "fileName" to fileName,
                "fileSize" to fileSize,
                "itemId" to item.id,
                "baseUrl" to baseUrl
            )

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .addTag("download")
                .addTag("item_${item.id}")
                .build()

            workManager.enqueueUniqueWork(
                "download_${item.id}",
                ExistingWorkPolicy.REPLACE, // Replace existing failed work
                workRequest
            )
        }
        
        // Clear failed downloads list optimistically, they will be re-added if they fail again
        _failedDownloads.value = emptySet()
        viewModelScope.launch { dataStoreManager.saveFailedDownloads(emptySet()) }
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
            
            // Check actual file size on disk (including partial downloads)
            val fileName = item.video?.fileName ?: "Unknown"
            val actualFileName = if (fileName.contains(".")) fileName else "$fileName.mp4"
            val file = File(getApplication<Application>().filesDir, "media_cache/$actualFileName")
            
            val downloadedBytes = if (file.exists() && file.length() > 0) {
                file.length()
            } else {
                0L
            }
            
            VideoDownloadProgress(
                itemId = item.id,
                fileName = fileName,
                fileSize = fileSize,
                downloadedBytes = downloadedBytes
            )
        }
        _videoProgressList.value = progressList
        Log.d(TAG, "📊 Initialized video progress list with ${progressList.size} items")
    }
    
    /**
     * Expose MediaCacheManager for UI components
     */
    fun getMediaCacheManager(): MediaCacheManager {
        return cacheManager
    }
}
