package com.logicalvalley.dsscreen.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.logicalvalley.dsscreen.ui.screens.DeviceRegistrationScreen
import com.logicalvalley.dsscreen.ui.screens.PlayerScreen
import com.logicalvalley.dsscreen.ui.screens.SplashScreen
import com.logicalvalley.dsscreen.ui.screens.VideoPlayerScreen
import com.logicalvalley.dsscreen.viewmodel.CacheViewModel
import com.logicalvalley.dsscreen.viewmodel.DeviceViewModel
import com.logicalvalley.dsscreen.viewmodel.TranscodingViewModel

/**
 * Navigation routes
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Registration : Screen("registration")
    object Player : Screen("player")
    object VideoPlayer : Screen("video_player")
}

/**
 * Navigation graph for the app
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: DeviceViewModel,
    cacheViewModel: CacheViewModel,
    transcodingViewModel: TranscodingViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash Screen (always first)
        composable(Screen.Splash.route) {
            SplashScreen(
                viewModel = viewModel,
                onNavigateToPlayer = {
                    navController.navigate(Screen.VideoPlayer.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToRegistration = {
                    navController.navigate(Screen.Registration.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        // Device Registration Screen
        composable(Screen.Registration.route) {
            DeviceRegistrationScreen(
                viewModel = viewModel,
                onRegistrationSuccess = {
                    // Auto-start playback after registration
                    navController.navigate(Screen.VideoPlayer.route) {
                        popUpTo(Screen.Registration.route) { inclusive = true }
                    }
                }
            )
        }

        // Player Screen (after registration)
        composable(Screen.Player.route) {
            val playlist by viewModel.playlist.collectAsState()
            
            PlayerScreen(
                viewModel = viewModel,
                cacheViewModel = cacheViewModel,
                transcodingViewModel = transcodingViewModel,
                onReRegister = {
                    navController.navigate(Screen.Registration.route) {
                        popUpTo(Screen.Player.route) { inclusive = true }
                    }
                },
                onStartPlayback = {
                    navController.navigate(Screen.VideoPlayer.route)
                }
            )
        }

        // Video Player Screen (full-screen playback)
        composable(Screen.VideoPlayer.route) {
            val currentPlaylist by viewModel.playlist.collectAsState()
            
            VideoPlayerScreen(
                playlist = currentPlaylist,
                viewModel = viewModel,
                cacheViewModel = cacheViewModel,
                transcodingViewModel = transcodingViewModel,
                onExit = {
                    // Go to Player screen to view details
                    navController.navigate(Screen.Player.route) {
                        popUpTo(Screen.VideoPlayer.route) { inclusive = true }
                    }
                },
                onDeRegister = {
                    viewModel.clearRegistration()
                    cacheViewModel.clearCache()
                    transcodingViewModel.clearTranscodedCache()
                    navController.navigate(Screen.Registration.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

