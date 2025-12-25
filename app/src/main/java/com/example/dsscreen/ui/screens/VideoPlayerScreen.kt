package com.example.dsscreen.ui.screens

import androidx.activity.compose.BackHandler
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.dsscreen.cache.VideoCacheManager
import com.example.dsscreen.cache.VideoDownloadManager
import com.example.dsscreen.data.model.Playlist
import com.example.dsscreen.player.PlaylistManager
import com.example.dsscreen.viewmodel.CacheViewModel
import com.example.dsscreen.viewmodel.DeviceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playlist: Playlist?,
    viewModel: DeviceViewModel,
    cacheViewModel: CacheViewModel,
    onExit: () -> Unit,
    onDeRegister: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Show loading if playlist is null or still loading
    if (isLoading || playlist == null || playlist.items == null || playlist.items!!.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = if (isLoading) "Loading playlist..." else "No videos in playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                // Show re-register button if not loading and still no playlist
                if (!isLoading && (playlist == null || playlist.items.isNullOrEmpty())) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.reloadPlaylist() },
                        modifier = Modifier.width(200.dp)
                    ) {
                        Text("Retry Loading")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = onDeRegister,
                        modifier = Modifier.width(200.dp)
                    ) {
                        Text("Re-register Device")
                    }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    var showControls by remember { mutableStateOf(false) }
    var showDeRegisterDialog by remember { mutableStateOf(false) }

    // Create playlist manager
    val playlistManager = remember(playlist) {
        val sortedItems = playlist.items?.sortedBy { it.order } ?: emptyList()
        PlaylistManager(sortedItems)
    }
    
    // Initialize cache status when playlist loads
    LaunchedEffect(playlist) {
        playlist?.let {
            android.util.Log.d("VideoPlayerScreen", "Initializing cache status for playlist")
            cacheViewModel.checkCacheStatus(it, "http://10.0.2.2:3000/")
        }
    }

    val currentItem by playlistManager.currentItem.collectAsState()
    var currentVideoIndex by remember { mutableStateOf(0) }
    var remainingTime by remember { mutableStateOf(0) }

    // Get cached data source factory (handles auto-caching)
    val cachedDataSourceFactory = remember { 
        android.util.Log.d("VideoPlayerScreen", "Creating cached data source factory")
        cacheViewModel.getCachedDataSourceFactory()
    }

    // Create ExoPlayer with cache support
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE // Loop current video
            playWhenReady = true
        }
    }

    // Track if player is ready to monitor caching
    var isPlayerReady by remember { mutableStateOf(false) }
    
    // Listen to player events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPlayerReady = true
                        android.util.Log.d("VideoPlayerScreen", "Player ready - video buffered")
                    }
                    Player.STATE_ENDED -> {
                        android.util.Log.d("VideoPlayerScreen", "Video ended - should be cached now")
                        // Update cache status
                        currentItem?.video?.id?.let { videoId ->
                            cacheViewModel.updateCacheStatusAfterPlay(videoId, "http://10.0.2.2:3000/")
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        android.util.Log.d("VideoPlayerScreen", "Player buffering...")
                    }
                    Player.STATE_IDLE -> {
                        android.util.Log.d("VideoPlayerScreen", "Player idle")
                    }
                }
            }
        }
        
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Load video when current item changes
    LaunchedEffect(currentItem) {
        currentItem?.let { item ->
            val videoUrl = playlistManager.getCurrentVideoUrl("http://10.0.2.2:3000/")
            videoUrl?.let { url ->
                android.util.Log.d("VideoPlayerScreen", "Loading video: $url")
                isPlayerReady = false
                
                try {
                    // Use cached data source factory (will use cache if available, otherwise stream and cache)
                    val mediaSource = ProgressiveMediaSource.Factory(cachedDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(url))

                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.play()

                    currentVideoIndex = playlistManager.getCurrentIndex()
                    remainingTime = playlistManager.getCurrentDuration()
                    
                    android.util.Log.d("VideoPlayerScreen", "Video loaded, preparing...")
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerScreen", "Error loading video", e)
                }
            }
        }
    }

    // Timer for video duration
    LaunchedEffect(currentItem) {
        val duration = playlistManager.getCurrentDuration()
        remainingTime = duration

        while (isActive && remainingTime > 0) {
            delay(1000)
            remainingTime--
            
            // Check cache status periodically (every 5 seconds)
            if (remainingTime % 5 == 0) {
                currentItem?.video?.id?.let { videoId ->
                    cacheViewModel.updateCacheStatusAfterPlay(videoId, "http://10.0.2.2:3000/")
                }
            }
        }

        // Final cache status update after video completes
        currentItem?.video?.id?.let { videoId ->
            android.util.Log.d("VideoPlayerScreen", "Video duration complete, updating cache status")
            cacheViewModel.updateCacheStatusAfterPlay(videoId, "http://10.0.2.2:3000/")
        }

        // Move to next video when duration expires
        if (remainingTime == 0) {
            playlistManager.moveToNext()
        }
    }

    // Cleanup player on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Handle back button - exit player directly
    BackHandler {
        exoPlayer.stop()
        onExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false // Hide default controls
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            // Long press anywhere to show de-register option
                            showControls = true
                        }
                    )
                }
        )

        // De-register dialog trigger (invisible button in corner for emergency exit)
        // Long press on screen edge to show de-register option
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        modifier = Modifier.width(500.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "Device Controls",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Divider()

                            // Playlist info
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Video ${currentVideoIndex + 1} of ${playlistManager.getTotalVideos()}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            Text(
                                text = playlistManager.getCurrentVideoName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Text(
                                text = "Time remaining: ${remainingTime}s",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Resume button
                            Button(
                                onClick = { showControls = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Resume"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resume Playback", fontSize = 16.sp)
                            }

                            // De-register button
                            Button(
                                onClick = { showDeRegisterDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "De-register"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("De-register Device", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // De-register confirmation dialog
    if (showDeRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showDeRegisterDialog = false },
            title = { Text("De-register Device?") },
            text = {
                Text("This will remove your device registration and stop playback. You'll need to re-register with a playlist code.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        exoPlayer.stop()
                        showDeRegisterDialog = false
                        onDeRegister()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("De-register")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeRegisterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

