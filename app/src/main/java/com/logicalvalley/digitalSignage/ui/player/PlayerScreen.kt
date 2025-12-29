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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.logicalvalley.digitalSignage.data.local.MediaCacheManager
import com.logicalvalley.digitalSignage.data.model.Playlist
import com.logicalvalley.digitalSignage.data.model.PlaylistItem
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlayerScreen(
    playlist: Playlist,
    onBack: () -> Unit,
    onError: (String, String) -> Unit
) {
    Log.d("PlayerScreen", "🎬 Starting playback for playlist: ${playlist.name}")
    val context = LocalContext.current
    val cacheManager = remember { MediaCacheManager(context) }
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentItem = playlist.items.getOrNull(currentIndex)

    if (currentItem != null) {
        val localFile = cacheManager.getLocalFile(currentItem)
        val isVideo = currentItem.video?.mimeType?.startsWith("video") == true
        
        if (isVideo) {
            VideoPlayer(
                item = currentItem,
                localFile = localFile,
                onFinished = {
                    currentIndex = (currentIndex + 1) % playlist.items.size
                },
                onError = { reason ->
                    onError(currentItem.video?.fileName ?: "Unknown", reason)
                }
            )
        } else {
            ImagePlayer(
                item = currentItem,
                localFile = localFile,
                onFinished = {
                    currentIndex = (currentIndex + 1) % playlist.items.size
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
    onFinished: () -> Unit,
    onError: (String) -> Unit
) {
    val durationMillis = (item.duration * 1000L).coerceAtLeast(1000L)
    val imageUrl = localFile ?: "http://10.0.2.2:3000/api/media/${item.video?.id}/download"
    Log.d("ImagePlayer", "🖼️ Displaying image: ${item.video?.fileName}, Local: ${localFile != null}")

    var hasError by remember(item.id) { mutableStateOf(false) }
    var errorMessage by remember(item.id) { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
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

    LaunchedEffect(item.id) {
        delay(durationMillis)
        if (!hasError) {
            onFinished()
        }
    }
}

@Composable
fun VideoPlayer(
    item: PlaylistItem,
    localFile: File?,
    onFinished: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val videoId = item.video?.id
    val videoName = item.video?.fileName ?: "Unknown"
    val videoUrl = "http://10.0.2.2:3000/api/media/$videoId/download"
    
    Log.d("VideoPlayer", "🎥 Initializing video: $videoName, Local: ${localFile != null}")
    
    var hasError by remember(item.id) { mutableStateOf(false) }
    var errorMessage by remember(item.id) { mutableStateOf("") }
    
    // Create player instance tied to the item ID
    val exoPlayer = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    // Effect to handle player setup and release
    DisposableEffect(item.id) {
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
            MediaItem.fromUri(localFile.absolutePath)
        } else {
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
    LaunchedEffect(item.id) {
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
                }
            },
            update = {
                it.player = exoPlayer
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
