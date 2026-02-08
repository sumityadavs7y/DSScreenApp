package com.logicalvalley.digitalSignage

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.logicalvalley.digitalSignage.data.local.DataStoreManager
import com.logicalvalley.digitalSignage.service.KeepAliveService
import com.logicalvalley.digitalSignage.ui.loading.LoadingScreen
import com.logicalvalley.digitalSignage.ui.player.PlayerScreen
import com.logicalvalley.digitalSignage.ui.registration.RegistrationScreen
import com.logicalvalley.digitalSignage.ui.theme.DigitalSignageLVTheme
import com.logicalvalley.digitalSignage.viewmodel.AppState
import com.logicalvalley.digitalSignage.viewmodel.MainViewModel

import androidx.activity.compose.BackHandler
import com.logicalvalley.digitalSignage.ui.stats.StatsScreen
import com.logicalvalley.digitalSignage.ui.settings.SettingsScreen
import com.logicalvalley.digitalSignage.ui.settings.CustomDisplayModeScreen

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var dataStoreManager: DataStoreManager
    
    // StateFlow to hold current rotation
    private val _currentRotation = MutableStateFlow("AUTO")
    private val currentRotation: StateFlow<String> = _currentRotation.asStateFlow()
    
    // StateFlow to hold current display mode
    private val _currentDisplayMode = MutableStateFlow("FIT")
    private val currentDisplayMode: StateFlow<String> = _currentDisplayMode.asStateFlow()
    
    // StateFlow to hold custom display modes (per item)
    private val _customDisplayModes = MutableStateFlow<Map<String, String>>(emptyMap())
    private val customDisplayModes: StateFlow<Map<String, String>> = _customDisplayModes.asStateFlow()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 MainActivity onCreate")
        
        dataStoreManager = DataStoreManager(this)
        
        // Keep screen on at all times
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide system UI for full-screen digital signage experience
        hideSystemUI()
        
        // Start foreground service to keep app running
        startKeepAliveService()
        
        // Request battery optimization exemption
        requestBatteryOptimizationExemption()
        
        // Load and apply saved rotation
        loadAndApplySavedRotation()
        
        // Load saved display mode
        loadSavedDisplayMode()
        
        // Load custom display modes
        loadCustomDisplayModes()
        
        setContent {
            DigitalSignageLVTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.appState.collectAsState()
                val licenseExpiry by viewModel.licenseExpiryDate.collectAsState()
                val playbackError by viewModel.playbackError.collectAsState()
                val isSocketConnected by viewModel.isSocketConnected.collectAsState()
                val remoteCommand by viewModel.remoteCommand.collectAsState()
                val failedDownloads by viewModel.failedDownloads.collectAsState()
                val isRetrying by viewModel.isRetrying.collectAsState()
                val storageStats by viewModel.storageStats.collectAsState()
                val currentDownloadProgress by viewModel.currentDownloadProgress.collectAsState()
                val overallDownloadStats by viewModel.overallDownloadStats.collectAsState()
                var showStats by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showCustomDisplayMode by remember { mutableStateOf(false) }
                
                // Collect current rotation and display mode from StateFlow
                val currentRotationValue by currentRotation.collectAsState()
                val currentDisplayModeValue by currentDisplayMode.collectAsState()
                val customDisplayModesValue by customDisplayModes.collectAsState()

                LaunchedEffect(state) {
                    if (state is AppState.RegistrationRequired) {
                        val qr = (state as AppState.RegistrationRequired).qrData
                        Log.d("MainActivity", "📱 Registration Screen Active. QR Data present: ${qr != null}")
                        if (qr != null) {
                            Log.d("MainActivity", "🔗 QR Data URL Length: ${qr.qrCodeDataUrl.length}")
                            Log.d("MainActivity", "🔗 QR Data URL Prefix: ${qr.qrCodeDataUrl.take(50)}...")
                        }
                    }
                }

                LaunchedEffect(remoteCommand) {
                    when (remoteCommand) {
                        "ENTER_FULLSCREEN" -> {
                            showStats = false
                            viewModel.clearRemoteCommand()
                        }
                        "EXIT_FULLSCREEN" -> {
                            showStats = true
                            viewModel.clearRemoteCommand()
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    when (val s = state) {
                        is AppState.Loading -> {
                            LoadingScreen()
                        }
                        is AppState.RegistrationRequired -> {
                            RegistrationScreen(
                                qrData = s.qrData,
                                error = s.error,
                                onRegister = { viewModel.register(it) },
                                onRefreshQr = { viewModel.initQrRegistration() }
                            )
                        }
                        is AppState.LicenseExpired -> {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "LICENSE EXPIRED",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Please contact support to renew your license.",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Expiry: ${licenseExpiry?.substringBefore("T") ?: "Unknown"}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Button(onClick = { viewModel.manualDeregister() }) {
                                        Text("Reset Registration")
                                    }
                                }
                            }
                        }
                        is AppState.Error -> {
                            RegistrationScreen(
                                qrData = null,
                                error = s.message,
                                onRegister = { viewModel.register(it) },
                                onRefreshQr = { viewModel.initQrRegistration() }
                            )
                        }
                        is AppState.Playing -> {
                            when {
                                showCustomDisplayMode -> {
                                    CustomDisplayModeScreen(
                                        playlist = s.playlist,
                                        customDisplayModes = customDisplayModesValue,
                                        onBack = { showCustomDisplayMode = false },
                                        onSetItemDisplayMode = { itemId, mode ->
                                            setItemDisplayMode(itemId, mode)
                                        }
                                    )
                                }
                                showSettings -> {
                                    SettingsScreen(
                                        currentRotation = currentRotationValue,
                                        currentDisplayMode = currentDisplayModeValue,
                                        onBack = { showSettings = false },
                                        onRotateClockwise = { rotateClockwise() },
                                        onRotateAntiClockwise = { rotateAntiClockwise() },
                                        onSetAutoRotation = { setAutoRotation() },
                                        onSetDisplayMode = { mode -> setDisplayMode(mode) },
                                        onOpenCustomDisplayMode = {
                                            setDisplayMode("CUSTOM")
                                            showSettings = false
                                            showCustomDisplayMode = true
                                        }
                                    )
                                }
                                showStats -> {
                                    StatsScreen(
                                        playlist = s.playlist,
                                        cacheProgress = s.cacheProgress,
                                        licenseExpiry = licenseExpiry,
                                        playbackError = playbackError,
                                        isSocketConnected = isSocketConnected,
                                        failedDownloadCount = failedDownloads.size,
                                        isRetrying = isRetrying,
                                        storageStats = storageStats,
                                        currentDownloadProgress = currentDownloadProgress,
                                        overallDownloadStats = overallDownloadStats,
                                        onBackToPlaylist = { showStats = false },
                                        onReset = { 
                                            showStats = false
                                            viewModel.manualDeregister() 
                                        },
                                        onOpenSettings = {
                                            showStats = false
                                            showSettings = true
                                        },
                                        onRetryDownloads = { viewModel.retryFailedDownloads() }
                                    )
                                }
                                else -> {
                                    BackHandler {
                                        showStats = true
                                    }
                                    PlayerScreen(
                                        playlist = s.playlist,
                                        displayMode = currentDisplayModeValue,
                                        customDisplayModes = customDisplayModesValue,
                                        onBack = { showStats = true },
                                        onError = { videoName, error ->
                                            viewModel.reportPlaybackError(videoName, error)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ MainActivity onResume")
        hideSystemUI()
        
        // Re-apply rotation to prevent device from overriding it
        lifecycleScope.launch {
            val savedRotation = _currentRotation.value
            if (savedRotation != "AUTO") {
                Log.d(TAG, "🔄 Re-applying rotation on resume: $savedRotation")
                applyRotation(savedRotation)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 MainActivity onDestroy")
    }

    /**
     * Start the foreground service to keep the app running in background
     */
    private fun startKeepAliveService() {
        try {
            val serviceIntent = Intent(this, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "✅ KeepAliveService started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start KeepAliveService", e)
        }
    }

    /**
     * Hide system UI (status bar, navigation bar) for immersive digital signage experience
     */
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Request exemption from battery optimization to prevent Android from killing the app
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Log.d(TAG, "📱 Requesting battery optimization exemption")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to request battery optimization exemption", e)
                }
            } else {
                Log.d(TAG, "✅ Battery optimization already disabled")
            }
        }
    }

    /**
     * Load saved rotation preference and apply it
     */
    private fun loadAndApplySavedRotation() {
        lifecycleScope.launch {
            val savedRotation = dataStoreManager.screenRotation.first() ?: "AUTO"
            Log.d(TAG, "🔄 Loading saved rotation: $savedRotation")
            _currentRotation.value = savedRotation
            applyRotation(savedRotation)
        }
    }

    /**
     * Rotate screen clockwise (90° rotation)
     */
    private fun rotateClockwise() {
        lifecycleScope.launch {
            val current = _currentRotation.value
            Log.d(TAG, "↻ Clockwise rotation from: $current")
            
            val nextRotation = if (current == "AUTO") {
                "90" // Start from landscape when rotating from auto
            } else {
                val currentAngle = current.toIntOrNull() ?: 0
                val nextAngle = (currentAngle + 90) % 360
                nextAngle.toString()
            }
            
            Log.d(TAG, "↻ Rotating to: $nextRotation")
            setScreenRotation(nextRotation)
        }
    }

    /**
     * Rotate screen anti-clockwise (270° rotation / -90°)
     */
    private fun rotateAntiClockwise() {
        lifecycleScope.launch {
            val current = _currentRotation.value
            Log.d(TAG, "↺ Anti-clockwise rotation from: $current")
            
            val nextRotation = if (current == "AUTO") {
                "270" // Start from reverse landscape when rotating from auto
            } else {
                val currentAngle = current.toIntOrNull() ?: 0
                val nextAngle = (currentAngle - 90 + 360) % 360
                nextAngle.toString()
            }
            
            Log.d(TAG, "↺ Rotating to: $nextRotation")
            setScreenRotation(nextRotation)
        }
    }

    /**
     * Set screen rotation to auto (device setting)
     */
    private fun setAutoRotation() {
        Log.d(TAG, "🔄 Setting rotation to AUTO")
        setScreenRotation("AUTO")
    }

    /**
     * Set screen rotation and save preference
     * @param rotation One of: "AUTO", "0", "90", "180", "270"
     */
    private fun setScreenRotation(rotation: String) {
        lifecycleScope.launch {
            // Update StateFlow immediately for UI
            _currentRotation.value = rotation
            
            // Apply rotation to activity
            applyRotation(rotation)
            
            // Save to DataStore for persistence
            dataStoreManager.saveScreenRotation(rotation)
            Log.d(TAG, "✅ Screen rotation saved: $rotation")
        }
    }

    /**
     * Apply rotation to the activity
     */
    private fun applyRotation(rotation: String) {
        val orientation = when (rotation) {
            "0" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "90" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "180" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            "270" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            "AUTO" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR // Follow device sensor
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        
        // Apply orientation immediately on main thread
        runOnUiThread {
            requestedOrientation = orientation
            Log.d(TAG, "🔄 Applied orientation: $rotation (${getOrientationName(orientation)})")
        }
    }
    
    /**
     * Get readable orientation name for logging
     */
    private fun getOrientationName(orientation: Int): String {
        return when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "PORTRAIT"
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "LANDSCAPE"
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "REVERSE_PORTRAIT"
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE"
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> "FULL_SENSOR"
            else -> "UNKNOWN"
        }
    }

    /**
     * Load saved display mode preference
     */
    private fun loadSavedDisplayMode() {
        lifecycleScope.launch {
            val savedMode = dataStoreManager.displayMode.first() ?: "FIT"
            Log.d(TAG, "📺 Loading saved display mode: $savedMode")
            _currentDisplayMode.value = savedMode
        }
    }

    /**
     * Set display mode and save preference
     * @param mode One of: "FIT", "FILL", "STRETCH", "CUSTOM"
     */
    private fun setDisplayMode(mode: String) {
        lifecycleScope.launch {
            Log.d(TAG, "📺 Setting display mode to: $mode")
            _currentDisplayMode.value = mode
            dataStoreManager.saveDisplayMode(mode)
            Log.d(TAG, "✅ Display mode saved: $mode")
        }
    }

    /**
     * Load custom display modes preference
     */
    private fun loadCustomDisplayModes() {
        lifecycleScope.launch {
            val savedModes = dataStoreManager.customDisplayModes.first()
            Log.d(TAG, "📺 Loading custom display modes: ${savedModes.size} items")
            _customDisplayModes.value = savedModes
        }
    }

    /**
     * Set display mode for a specific playlist item
     * @param itemId The playlist item ID
     * @param mode One of: "FIT", "FILL", "STRETCH"
     */
    private fun setItemDisplayMode(itemId: String, mode: String) {
        lifecycleScope.launch {
            Log.d(TAG, "📺 Setting display mode for item $itemId to: $mode")
            dataStoreManager.saveItemDisplayMode(itemId, mode)
            
            // Update local state
            val updatedModes = _customDisplayModes.value.toMutableMap()
            updatedModes[itemId] = mode
            _customDisplayModes.value = updatedModes
            
            Log.d(TAG, "✅ Item display mode saved: $itemId = $mode")
        }
    }
}
