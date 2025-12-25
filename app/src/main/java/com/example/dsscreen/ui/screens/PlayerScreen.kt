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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsscreen.data.model.Playlist
import com.example.dsscreen.data.model.PlaylistItem
import com.example.dsscreen.viewmodel.CacheViewModel
import com.example.dsscreen.viewmodel.DeviceViewModel

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
                .focusable(),
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

            // Transcoding Status Card - Enhanced with error handling
            if (transcodingJobs.isNotEmpty() || transcodedVideos.isNotEmpty()) {
                val inProgressJobs = transcodingJobs.values.filter { it.status == com.example.dsscreen.transcoding.TranscodingStatus.IN_PROGRESS }
                val completedJobs = transcodingJobs.values.filter { it.status == com.example.dsscreen.transcoding.TranscodingStatus.COMPLETED } + transcodedVideos.map { 
                    com.example.dsscreen.transcoding.TranscodingJob(it, "", "", "", com.example.dsscreen.transcoding.TranscodingStatus.COMPLETED, 1f)
                }
                val failedJobs = transcodingJobs.values.filter { it.status == com.example.dsscreen.transcoding.TranscodingStatus.FAILED }
                
                val hasErrors = failedJobs.isNotEmpty()
                val hasActive = inProgressJobs.isNotEmpty()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            hasErrors -> MaterialTheme.colorScheme.errorContainer
                            !hasActive && completedJobs.isNotEmpty() -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = when {
                                        hasErrors -> "⚠️ Video Conversion Issues"
                                        hasActive -> "⟳ Converting Videos"
                                        else -> "✓ Video Conversion Complete"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = when {
                                        hasErrors -> MaterialTheme.colorScheme.error
                                        !hasActive -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                
                                // Summary counts
                                val summaryParts = mutableListOf<String>()
                                if (inProgressJobs.isNotEmpty()) summaryParts.add("${inProgressJobs.size} converting")
                                if (completedJobs.isNotEmpty()) summaryParts.add("${completedJobs.size} completed")
                                if (failedJobs.isNotEmpty()) summaryParts.add("${failedJobs.size} failed")
                                
                                if (summaryParts.isNotEmpty()) {
                                    Text(
                                        text = summaryParts.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            if (hasActive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Device info
                        val (optWidth, optHeight) = transcodingViewModel.getDeviceOptimalResolution()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📱",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Column {
                                    Text(
                                        text = "Device Target: ${optWidth}x${optHeight}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "High-resolution videos are automatically converted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        
                        // ACTIVE CONVERSIONS
                        if (inProgressJobs.isNotEmpty()) {
                            Divider()
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔄",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Converting Now",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            inProgressJobs.forEach { job ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "Video ${job.videoId.take(8)}...",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Target: ${job.targetResolution}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            
                                            Text(
                                                text = "${(job.progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        LinearProgressIndicator(
                                            progress = job.progress.coerceIn(0f, 1f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        
                                        if (job.progress > 0f) {
                                            Text(
                                                text = if (job.progress < 0.3f) "📥 Processing video..." 
                                                       else if (job.progress < 0.7f) "🎬 Converting frames..." 
                                                       else "✨ Finalizing...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // COMPLETED CONVERSIONS
                        if (completedJobs.isNotEmpty()) {
                            Divider()
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✅",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Successfully Converted",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${completedJobs.size}",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column {
                                        Text(
                                            text = "video${if (completedJobs.size != 1) "s" else ""} ready",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Converted versions are now being used for smooth playback",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // FAILED CONVERSIONS
                        if (failedJobs.isNotEmpty()) {
                            Divider()
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "❌",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Conversion Failed",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            failedJobs.forEach { job ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Video ${job.videoId.take(8)}...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "FAILED",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onError
                                                )
                                            }
                                        }
                                        
                                        // Error message
                                        val errorMessage = when {
                                            job.error?.contains("resolution too high", ignoreCase = true) == true ->
                                                "Video resolution is too high for this device to convert. Please use a lower resolution source video or test on a physical device."
                                            job.error?.contains("decode", ignoreCase = true) == true ->
                                                "Unable to decode video. The video format or codec may not be supported."
                                            job.error?.contains("codec", ignoreCase = true) == true ->
                                                "Codec error occurred. Your device may not support converting this video format."
                                            job.error != null -> job.error
                                            else -> "Conversion failed due to an unknown error."
                                        }
                                        
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Why this happened:",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = errorMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        
                                        // Helpful suggestions
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFF2196F3).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "💡",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "What you can do:",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color(0xFF1976D2)
                                                    )
                                                    Text(
                                                        text = "• Use a physical Android device for better support\n• Pre-convert videos to 1080p or 720p on your computer\n• The video will be skipped during playback",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Cache info at bottom
                        val cacheSize = transcodingViewModel.getTranscodedCacheSizeFormatted()
                        if (cacheSize != "0KB") {
                            Divider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💾 Storage Used: $cacheSize",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
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
                    Button(
                        onClick = { onStartPlayback() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
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
            Button(
                onClick = {
                    viewModel.clearRegistration()
                    onReRegister()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp)
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

