package com.logicalvalley.digitalSignage

import android.os.Bundle
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.logicalvalley.digitalSignage.ui.player.PlayerScreen
import com.logicalvalley.digitalSignage.ui.registration.RegistrationScreen
import com.logicalvalley.digitalSignage.ui.theme.DigitalSignageLVTheme
import com.logicalvalley.digitalSignage.viewmodel.AppState
import com.logicalvalley.digitalSignage.viewmodel.MainViewModel

import androidx.activity.compose.BackHandler
import com.logicalvalley.digitalSignage.ui.stats.StatsScreen

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalSignageLVTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.appState.collectAsState()
                val licenseExpiry by viewModel.licenseExpiryDate.collectAsState()
                val playbackError by viewModel.playbackError.collectAsState()
                var showStats by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    when (val s = state) {
                        is AppState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is AppState.RegistrationRequired -> {
                            RegistrationScreen(
                                onRegister = { viewModel.register(it) }
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
                                    Button(onClick = { viewModel.resetRegistration() }) {
                                        Text("Reset Registration")
                                    }
                                }
                            }
                        }
                        is AppState.Error -> {
                            RegistrationScreen(
                                onRegister = { viewModel.register(it) },
                                error = s.message
                            )
                        }
                        is AppState.Playing -> {
                            if (showStats) {
                                StatsScreen(
                                    playlist = s.playlist,
                                    cacheProgress = s.cacheProgress,
                                    licenseExpiry = licenseExpiry,
                                    playbackError = playbackError,
                                    onBackToPlaylist = { showStats = false },
                                    onReset = { 
                                        showStats = false
                                        viewModel.resetRegistration() 
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
}
