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
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playlist: Playlist?,
    viewModel: DeviceViewModel,
    cacheViewModel: CacheViewModel,
    transcodingViewModel: com.example.dsscreen.viewmodel.TranscodingViewModel,
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
    
    // Transcoding state
    val transcodedVideos by transcodingViewModel.transcodedVideos.collectAsState()
    val transcodingJobs by transcodingViewModel.transcodingJobs.collectAsState()

    // Get cached data source factory (handles auto-caching)
    val cachedDataSourceFactory = remember { 
        android.util.Log.d("VideoPlayerScreen", "Creating cached data source factory")
        cacheViewModel.getCachedDataSourceFactory()
    }

    // Create ExoPlayer with cache support and adaptive resolution
    val exoPlayer = remember {
        val displayMetrics = context.resources.displayMetrics
        val maxWidth = displayMetrics.widthPixels
        val maxHeight = displayMetrics.heightPixels
        
        android.util.Log.d("VideoPlayerScreen", 
            "Device display: ${maxWidth}x${maxHeight}")
        
        // Create a custom renderers factory that enables decoder fallback
        // This will try all available hardware decoders before failing
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true) // Enable fallback to alternative hardware decoders
            .setAllowedVideoJoiningTimeMs(5000) // Allow smoother transitions
        
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setHandleAudioBecomingNoisy(true) // Better audio handling
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE // Loop current video
                playWhenReady = true
                
                // Configure track selection to prefer device-compatible formats
                // Note: This only works with adaptive streaming (HLS/DASH), not single MP4 files
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(maxWidth, maxHeight)
                    .setMaxVideoBitrate(15_000_000) // 15 Mbps max (reduced for better compatibility)
                    .setForceHighestSupportedBitrate(false) // Prefer supported over highest
                    .build()
                
                android.util.Log.d("VideoPlayerScreen", 
                    "ExoPlayer configured:\n" +
                    "  - Device display: ${maxWidth}x${maxHeight}\n" +
                    "  - Max bitrate: 15Mbps\n" +
                    "  - Hardware decoder fallback: ENABLED\n" +
                    "  - Video scaling: SCALE_TO_FIT_WITH_CROPPING\n" +
                    "  - Auto-skip on error: ENABLED")
            }
    }

    // Track if player is ready to monitor caching
    var isPlayerReady by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // Listen to player events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPlayerReady = true
                        playbackError = null // Clear any previous errors
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
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val isDecoderError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
                
                val errorMessage = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                        "Video resolution too high - converting in background..."
                    }
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                        "Video codec not supported - converting in background..."
                    }
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        "Network error - check your connection"
                    }
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                        "Video file not found or unavailable"
                    }
                    else -> {
                        "Playback error: ${error.message}"
                    }
                }
                
                playbackError = errorMessage
                android.util.Log.e("VideoPlayerScreen", 
                    "Playback error: ${error.errorCode} - $errorMessage\n" +
                    "Format supported: ${error.message}", error)
                
                // For decoder errors, trigger transcoding
                if (isDecoderError) {
                    currentItem?.video?.let { video ->
                        val videoId = video.id
                        val videoUrl = playlistManager.getCurrentVideoUrl("http://10.0.2.2:3000/") ?: return@let
                        
                        android.util.Log.d("VideoPlayerScreen", "=".repeat(60))
                        android.util.Log.d("VideoPlayerScreen", "🎬 DECODER ERROR - Triggering transcoding")
                        android.util.Log.d("VideoPlayerScreen", "Video ID: $videoId")
                        android.util.Log.d("VideoPlayerScreen", "Video URL: $videoUrl")
                        android.util.Log.d("VideoPlayerScreen", "=".repeat(60))
                        
                        // Export cached video and start transcoding
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            android.util.Log.d("VideoPlayerScreen", "Step 1: Exporting cached video...")
                            
                            val sourceFile = cacheViewModel.downloadManager.exportCachedVideoToFile(videoUrl)
                            
                            if (sourceFile != null) {
                                android.util.Log.d("VideoPlayerScreen", "Step 2: ✅ Export successful: $sourceFile")
                                android.util.Log.d("VideoPlayerScreen", "Step 3: Requesting transcoding...")
                                
                                val started = transcodingViewModel.requestTranscoding(videoId, sourceFile)
                                
                                if (started) {
                                    android.util.Log.d("VideoPlayerScreen", "Step 4: ✅ Transcoding request accepted")
                                } else {
                                    android.util.Log.w("VideoPlayerScreen", "Step 4: ⚠️ Transcoding request rejected (already in progress or complete)")
                                }
                            } else {
                                android.util.Log.e("VideoPlayerScreen", "Step 2: ❌ Export failed - cannot transcode")
                            }
                        }
                    }
                }
                
                // Show error for 2 seconds, then skip to next video
                val delayMs = 2000L
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(delayMs)
                    if (playbackError != null) {
                        android.util.Log.d("VideoPlayerScreen", "Auto-skipping to next video due to error (${delayMs}ms delay)")
                        
                        // Try to skip to next video
                        val currentIndex = playlistManager.getCurrentIndex()
                        playlistManager.moveToNext()
                        val newIndex = playlistManager.getCurrentIndex()
                        
                        // If we're back at the same video or only one video exists, show persistent error
                        if (currentIndex == newIndex || playlistManager.getTotalVideos() == 1) {
                            android.util.Log.e("VideoPlayerScreen", "Only one video or all videos failed. Keeping error visible.")
                            // Don't clear error - keep it visible
                        } else {
                            playbackError = null // Clear error and try next video
                        }
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
            val videoId = item.video?.id
            val videoUrl = playlistManager.getCurrentVideoUrl("http://10.0.2.2:3000/")
            
            videoUrl?.let { url ->
                isPlayerReady = false
                playbackError = null // Clear previous errors when loading new video
                
                try {
                    // Check if a transcoded version exists
                    val transcodedPath = if (videoId != null) {
                        transcodingViewModel.getTranscodedVideoPath(videoId)
                    } else null
                    
                    val mediaSource = if (transcodedPath != null) {
                        // Use transcoded local file
                        android.util.Log.d("VideoPlayerScreen", "✅ Using transcoded video: $transcodedPath")
                        ProgressiveMediaSource.Factory(androidx.media3.datasource.DefaultDataSource.Factory(context))
                            .createMediaSource(MediaItem.fromUri("file://$transcodedPath"))
                    } else {
                        // Use original video (cached or streamed)
                        android.util.Log.d("VideoPlayerScreen", "Loading original video: $url")
                        ProgressiveMediaSource.Factory(cachedDataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(url))
                    }

                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.play()

                    currentVideoIndex = playlistManager.getCurrentIndex()
                    remainingTime = playlistManager.getCurrentDuration()
                    
                    android.util.Log.d("VideoPlayerScreen", "Video loaded, preparing...")
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerScreen", "Error loading video", e)
                    playbackError = "Failed to load video: ${e.message}"
                }
            }
        }
    }
    
    // Watch for transcoding completions and trigger video reload
    LaunchedEffect(transcodedVideos) {
        transcodedVideos.forEach { videoId ->
            android.util.Log.d("VideoPlayerScreen", "🎬 Video $videoId transcoded successfully!")
            
            // If this is the current video and it had an error, try reloading it
            if (currentItem?.video?.id == videoId && (playbackError != null || !isPlayerReady)) {
                android.util.Log.d("VideoPlayerScreen", "Reloading current video with transcoded version")
                playbackError = null
                
                // Trigger reload by temporarily setting to null then back
                val tempItem = currentItem
                kotlinx.coroutines.delay(100)
                // Force reload will happen via the LaunchedEffect above when currentItem changes
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

        // Error message overlay
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                        .fillMaxWidth(0.8f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Text(
                            text = "Video Playback Error",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Text(
                            text = playbackError ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Show helpful message based on error type
                        Text(
                            text = if (playbackError?.contains("converting") == true) {
                                "💡 Video will be converted(if possible) and cached for future playback"
                            } else if (playbackError?.contains("Network") == true) {
                                "💡 Check your network connection and backend server"
                            } else {
                                "💡 Check video format and try a different video"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                            // Manual skip button
                            Button(
                                onClick = {
                                    playbackError = null
                                    playlistManager.moveToNext()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text("Skip Now")
                            }
                            
                            // Retry button
                            OutlinedButton(
                                onClick = {
                                    playbackError = null
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Retry")
                            }
                            }
                        }
                        
                        Text(
                            text = "Auto-skipping in 2 seconds...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }

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

