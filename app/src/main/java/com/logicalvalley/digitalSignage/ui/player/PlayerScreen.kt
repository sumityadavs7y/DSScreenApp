package com.logicalvalley.digitalSignage.ui.player

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.logicalvalley.digitalSignage.config.AppConfig
import com.logicalvalley.digitalSignage.data.local.MediaCacheManager
import com.logicalvalley.digitalSignage.data.model.Playlist
import com.logicalvalley.digitalSignage.data.model.PlaylistItem
import com.logicalvalley.digitalSignage.util.SSLConfig
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlayerScreen(
    playlist: Playlist,
    displayMode: String,
    customDisplayModes: Map<String, String>,
    onBack: () -> Unit,
    onError: (String, String) -> Unit
) {
    Log.d("PlayerScreen", "🎬 Starting playback for playlist: ${playlist.name}")
    val context = LocalContext.current
    val cacheManager = remember { MediaCacheManager(context) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var playCounter by remember { mutableIntStateOf(0) }
    val currentItem = playlist.items.getOrNull(currentIndex)

    if (currentItem != null) {
        // Use cached file if available
        val localFile = cacheManager.getLocalFile(currentItem)
        val isVideo = currentItem.video?.mimeType?.startsWith("video") == true
        val playKey = "${currentItem.id}_$playCounter"
        
        // Determine actual display mode: use custom if mode is CUSTOM, otherwise use global
        val actualDisplayMode = if (displayMode == "CUSTOM") {
            customDisplayModes[currentItem.id] ?: "FIT"
        } else {
            displayMode
        }
        
        if (isVideo) {
            VideoPlayer(
                item = currentItem,
                localFile = localFile,
                playKey = playKey,
                displayMode = actualDisplayMode,
                onFinished = {
                    currentIndex = (currentIndex + 1) % playlist.items.size
                    playCounter++
                },
                onError = { reason ->
                    onError(currentItem.video?.fileName ?: "Unknown", reason)
                }
            )
        } else {
            ImagePlayer(
                item = currentItem,
                localFile = localFile,
                playKey = playKey,
                displayMode = actualDisplayMode,
                onFinished = {
                    currentIndex = (currentIndex + 1) % playlist.items.size
                    playCounter++
                },
                onError = { reason ->
                    onError(currentItem.video?.fileName ?: "Unknown", reason)
                }
            )
        }
    }
}

@Composable
fun ImagePlayer(
    item: PlaylistItem,
    localFile: File?,
    playKey: String,
    displayMode: String,
    onFinished: () -> Unit,
    onError: (String) -> Unit
) {
    val durationMillis = (item.duration * 1000L).coerceAtLeast(1000L)
    val imageUrl = localFile ?: "${AppConfig.BASE_URL}/api/media/${item.video?.id}/download"
    
    if (localFile != null && localFile.exists()) {
        Log.d("ImagePlayer", "🖼️ Displaying image: ${item.video?.fileName}, PlayKey: $playKey, Mode: $displayMode, 📦 Using cached file")
    } else {
        Log.d("ImagePlayer", "🖼️ Displaying image: ${item.video?.fileName}, PlayKey: $playKey, Mode: $displayMode, 🌐 Streaming from URL")
    }

    var hasError by remember(playKey) { mutableStateOf(false) }
    var errorMessage by remember(playKey) { mutableStateOf("") }

    // Map display mode to ContentScale
    val contentScale = when (displayMode) {
        "FIT" -> ContentScale.Fit
        "FILL" -> ContentScale.Crop
        "STRETCH" -> ContentScale.FillBounds
        else -> ContentScale.Fit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onError = { result ->
                val error = result.result.throwable.message ?: "Failed to load image"
                Log.e("ImagePlayer", "❌ Error loading image: $error")
                errorMessage = error
                hasError = true
                onError(error)
            }
        )

        if (hasError) {
            ErrorDialog(
                message = errorMessage,
                onSkip = onFinished
            )
        }
    }

    LaunchedEffect(playKey) {
        delay(durationMillis)
        if (!hasError) {
            onFinished()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    item: PlaylistItem,
    localFile: File?,
    playKey: String,
    displayMode: String,
    onFinished: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val videoId = item.video?.id
    val videoName = item.video?.fileName ?: "Unknown"
    val videoUrl = "${AppConfig.BASE_URL}/api/media/$videoId/download"
    
    Log.d("VideoPlayer", "🎥 Initializing video: $videoName, PlayKey: $playKey, Mode: $displayMode, Local: ${localFile != null}")
    
    var hasError by remember(playKey) { mutableStateOf(false) }
    var errorMessage by remember(playKey) { mutableStateOf("") }
    
    // Map display mode to ExoPlayer resize mode
    val playerResizeMode = when (displayMode) {
        "FIT" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "FILL" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        "STRETCH" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    
    // Create player instance tied to the play key with SSL-configured OkHttp
    val exoPlayer = remember(playKey) {
        // Use DefaultDataSource for both HTTP and local file support
        val httpDataSourceFactory = OkHttpDataSource.Factory(SSLConfig.createOkHttpClient())
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
            }
    }

    // Effect to handle player setup and release
    DisposableEffect(playKey) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("VideoPlayer", "❌ Playback error for $videoName: ${error.message}")
                errorMessage = error.message ?: "Playback error"
                hasError = true
                onError(errorMessage)
            }
        }
        exoPlayer.addListener(listener)

        val mediaItem = if (localFile != null && localFile.exists()) {
            Log.d("VideoPlayer", "📦 Using cached file: ${localFile.name}")
            Log.d("VideoPlayer", "📂 File path: ${localFile.absolutePath}")
            Log.d("VideoPlayer", "📊 File size: ${localFile.length() / 1024}KB")
            Log.d("VideoPlayer", "🔓 Can read: ${localFile.canRead()}")
            
            // Verify file is valid before creating MediaItem
            if (!localFile.canRead()) {
                Log.e("VideoPlayer", "❌ Cannot read file, falling back to streaming")
                MediaItem.fromUri(videoUrl)
            } else if (localFile.length() < 1024) {
                Log.e("VideoPlayer", "❌ File too small (${localFile.length()} bytes), falling back to streaming")
                MediaItem.fromUri(videoUrl)
            } else {
                val fileUri = android.net.Uri.fromFile(localFile)
                Log.d("VideoPlayer", "🎬 File URI: $fileUri")
                MediaItem.fromUri(fileUri)
            }
        } else {
            if (localFile != null) {
                Log.w("VideoPlayer", "⚠️ Local file doesn't exist: ${localFile.absolutePath}")
            }
            Log.d("VideoPlayer", "🌐 Streaming from URL: $videoUrl")
            MediaItem.fromUri(videoUrl)
        }
        
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        onDispose {
            Log.d("VideoPlayer", "♻️ Releasing player for $videoName")
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Effect to handle duration-based skipping
    LaunchedEffect(playKey) {
        val durationMillis = (item.duration * 1000L).coerceAtLeast(1000L)
        delay(durationMillis)
        if (!hasError) {
            Log.d("VideoPlayer", "🕒 Duration reached for $videoName, skipping...")
            onFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = playerResizeMode
                }
            },
            update = {
                it.player = exoPlayer
                it.resizeMode = playerResizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (hasError) {
            ErrorDialog(
                message = errorMessage,
                onSkip = {
                    hasError = false
                    onFinished()
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorDialog(
    message: String,
    onSkip: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        onSkip()
    }

    Dialog(onDismissRequest = onSkip) {
        Surface(
            modifier = Modifier.width(450.dp).padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Playback Error",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Automatically skipping in $timeLeft seconds...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onSkip) {
                    Text("Skip Now")
                }
            }
        }
    }
}
