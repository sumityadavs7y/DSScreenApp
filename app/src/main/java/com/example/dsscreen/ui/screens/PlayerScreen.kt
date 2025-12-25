package com.example.dsscreen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsscreen.data.model.Playlist
import com.example.dsscreen.data.model.PlaylistItem
import com.example.dsscreen.viewmodel.CacheViewModel
import com.example.dsscreen.viewmodel.DeviceViewModel
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    viewModel: DeviceViewModel,
    cacheViewModel: CacheViewModel,
    transcodingViewModel: com.example.dsscreen.viewmodel.TranscodingViewModel,
    onReRegister: () -> Unit,
    onStartPlayback: () -> Unit
) {
    val playlist by viewModel.playlist.collectAsState()
    val registrationData by viewModel.registrationData.collectAsState(initial = emptyMap())
    val videoCacheStatus by cacheViewModel.videoCacheStatus.collectAsState()
    val overallProgress by cacheViewModel.overallProgress.collectAsState()
    
    // Transcoding state
    val transcodingJobs by transcodingViewModel.transcodingJobs.collectAsState()
    val transcodedVideos by transcodingViewModel.transcodedVideos.collectAsState()
    
    // Check cache status when screen loads and periodically
    LaunchedEffect(playlist) {
        playlist?.let {
            // Initial check
            cacheViewModel.checkCacheStatus(it, "http://10.0.2.2:3000/")
            
            // Periodic refresh every 10 seconds while on this screen
            while (true) {
                kotlinx.coroutines.delay(10000) // 10 seconds
                cacheViewModel.checkCacheStatus(it, "http://10.0.2.2:3000/")
            }
        }
    }

    val scrollState = rememberScrollState()
    val columnFocusRequester = remember { FocusRequester() }
    val playbackButtonFocusRequester = remember { FocusRequester() }
    val reregisterButtonFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    
    var columnHasFocus by remember { mutableStateOf(true) }
    
    // Auto-focus column when screen loads
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        columnFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(32.dp)
                .focusRequester(columnFocusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    columnHasFocus = focusState.isFocused
                }
                .onKeyEvent { keyEvent ->
                    if (columnHasFocus && keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionDown, Key.PageDown -> {
                                val scrollAmount = if (keyEvent.key == Key.PageDown) 400 else 200
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(
                                        (scrollState.value + scrollAmount).coerceAtMost(scrollState.maxValue)
                                    )
                                }
                                true
                            }
                            Key.DirectionUp, Key.PageUp -> {
                                val scrollAmount = if (keyEvent.key == Key.PageUp) 400 else 200
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(
                                        (scrollState.value - scrollAmount).coerceAtLeast(0)
                                    )
                                }
                                true
                            }
                            Key.Enter, Key.DirectionCenter -> {
                                // Navigate to playback button when pressing center/enter
                                playbackButtonFocusRequester.requestFocus()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device Registered",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Playlist: ${playlist?.name ?: registrationData["playlistName"] ?: "Unknown"}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Code: ${registrationData["playlistCode"] ?: "N/A"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            // Cache Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (overallProgress >= 1f) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (overallProgress >= 1f) "✓ Ready for Offline" else "⟳ Caching Videos",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (overallProgress >= 1f) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (overallProgress >= 1f) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Segmented progress bar
                    playlist?.items?.let { items ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items.sortedBy { it.order }.forEach { item ->
                                val videoId = item.video?.id ?: ""
                                val cacheInfo = videoCacheStatus[videoId]
                                val isCached = cacheInfo?.isCached ?: false
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .background(
                                            color = if (isCached) 
                                                MaterialTheme.colorScheme.primary 
                                            else Color(0xFFCCCCCC),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = if (overallProgress >= 1f) 
                            "All videos cached. App will work offline." 
                        else "Videos are downloading automatically in background for offline playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Transcoding Status Card
            if (transcodingJobs.isNotEmpty() || transcodedVideos.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (transcodingJobs.isEmpty()) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (transcodingJobs.isEmpty()) "✓ Video Conversion Complete" else "⟳ Converting Videos",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (transcodingJobs.isEmpty()) 
                                    MaterialTheme.colorScheme.tertiary 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (transcodingJobs.isNotEmpty()) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Show device optimal resolution
                        val (optWidth, optHeight) = transcodingViewModel.getDeviceOptimalResolution()
                        Text(
                            text = "Target Resolution: ${optWidth}x${optHeight}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        // Active transcoding jobs
                        if (transcodingJobs.isNotEmpty()) {
                            Text(
                                text = "Converting (${transcodingJobs.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            transcodingJobs.values.forEach { job ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Video ${job.videoId.take(8)}...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${(job.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = job.progress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                }
                            }
                        }
                        
                        // Completed transcodings
                        if (transcodedVideos.isNotEmpty()) {
                            Text(
                                text = "Completed (${transcodedVideos.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            
                            Text(
                                text = "${transcodedVideos.size} video(s) converted and ready for playback",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        
                        // Cache size info
                        val cacheSize = transcodingViewModel.getTranscodedCacheSizeFormatted()
                        if (cacheSize != "0KB") {
                            Text(
                                text = "Transcoded cache: $cacheSize",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        
                        Text(
                            text = if (transcodingJobs.isEmpty()) 
                                "High-resolution videos have been converted to optimal format for your device." 
                            else "Videos are being converted in the background. Playlist continues playing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Device Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Device Information",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider()
                    InfoRow("Device UID", registrationData["deviceUid"] ?: "N/A")
                    InfoRow("Device ID", registrationData["deviceId"] ?: "N/A")
                    InfoRow("Registered At", registrationData["registeredAt"] ?: "N/A")
                }
            }

            // Start Playback Button
            playlist?.items?.let { items ->
                if (items.isNotEmpty()) {
                    var isPlaybackFocused by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = { onStartPlayback() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusRequester(playbackButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                isPlaybackFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    // Scroll to show button when focused
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(
                                            (scrollState.value + 300).coerceAtMost(scrollState.maxValue)
                                        )
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (isPlaybackFocused && keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            // Move to re-register button
                                            reregisterButtonFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            // Return focus to scrollable area
                                            columnFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaybackFocused)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isPlaybackFocused)
                            androidx.compose.foundation.BorderStroke(4.dp, Color.White)
                        else null
                    ) {
                        Text(
                            text = "▶ Start Playback (${items.size} videos)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Playlist Items
            playlist?.items?.let { items ->
                if (items.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Playlist Videos (${items.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Divider()

                            // Display playlist items (not using LazyColumn since parent is scrollable)
                            items.sortedBy { it.order }.forEach { item ->
                                PlaylistItemCard(item)
                            }
                        }
                    }
                }
            }

            // Re-register Button
            var isReregisterFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    viewModel.clearRegistration()
                    onReRegister()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(reregisterButtonFocusRequester)
                    .onFocusChanged { focusState ->
                        isReregisterFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            // Scroll to show button when focused
                            coroutineScope.launch {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    }
                    .onKeyEvent { keyEvent ->
                        if (isReregisterFocused && keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp -> {
                                    // Move back to playback button
                                    playbackButtonFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReregisterFocused)
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (isReregisterFocused)
                    androidx.compose.foundation.BorderStroke(4.dp, Color.White)
                else null
            ) {
                Text(
                    text = "Re-register Device",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PlaylistItemCard(item: PlaylistItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Video #${item.order + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.video?.fileName ?: "Unknown Video",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.video?.resolution?.let { resolution ->
                    Text(
                        text = resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${item.duration}s",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

