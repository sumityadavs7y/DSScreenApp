package com.logicalvalley.digitalSignage

import android.content.Intent
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
import com.logicalvalley.digitalSignage.service.KeepAliveService
import com.logicalvalley.digitalSignage.ui.loading.LoadingScreen
import com.logicalvalley.digitalSignage.ui.player.PlayerScreen
import com.logicalvalley.digitalSignage.ui.registration.RegistrationScreen
import com.logicalvalley.digitalSignage.ui.theme.DigitalSignageLVTheme
import com.logicalvalley.digitalSignage.viewmodel.AppState
import com.logicalvalley.digitalSignage.viewmodel.MainViewModel

import androidx.activity.compose.BackHandler
import com.logicalvalley.digitalSignage.ui.stats.StatsScreen

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 MainActivity onCreate")
        
        // Keep screen on at all times
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide system UI for full-screen digital signage experience
        hideSystemUI()
        
        // Start foreground service to keep app running
        startKeepAliveService()
        
        // Request battery optimization exemption
        requestBatteryOptimizationExemption()
        setContent {
            DigitalSignageLVTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.appState.collectAsState()
                val licenseExpiry by viewModel.licenseExpiryDate.collectAsState()
                val playbackError by viewModel.playbackError.collectAsState()
                val isSocketConnected by viewModel.isSocketConnected.collectAsState()
                val remoteCommand by viewModel.remoteCommand.collectAsState()
                var showStats by remember { mutableStateOf(false) }

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
                            if (showStats) {
                                StatsScreen(
                                    playlist = s.playlist,
                                    cacheProgress = s.cacheProgress,
                                    licenseExpiry = licenseExpiry,
                                    playbackError = playbackError,
                                    isSocketConnected = isSocketConnected,
                                    onBackToPlaylist = { showStats = false },
                                    onReset = { 
                                        showStats = false
                                        viewModel.manualDeregister() 
                                    }
                                )
                            } else {
                                BackHandler {
                                    showStats = true
                                }
                                PlayerScreen(
                                    playlist = s.playlist,
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

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "▶️ MainActivity onResume")
        hideSystemUI()
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
}
