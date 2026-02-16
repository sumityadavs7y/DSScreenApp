package com.logicalvalley.digitalSignage.ui.stats

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.material3.LinearProgressIndicator
import com.logicalvalley.digitalSignage.data.model.Playlist
import com.logicalvalley.digitalSignage.data.model.PlaybackErrorInfo
import com.logicalvalley.digitalSignage.data.model.PlaylistItem
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

import com.logicalvalley.digitalSignage.data.local.MediaCacheManager

/**
 * Robust UI for displaying device and playlist statistics.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StatsScreen(
    playlist: Playlist,
    cacheProgress: Float,
    licenseExpiry: String?,
    playbackError: PlaybackErrorInfo?,
    isSocketConnected: Boolean,
    failedDownloadCount: Int,
    isRetrying: Boolean,
    storageStats: MediaCacheManager.StorageStats?,
    mediaCacheManager: MediaCacheManager,
    videoProgressList: List<com.logicalvalley.digitalSignage.viewmodel.MainViewModel.VideoDownloadProgress>,
    onBackToPlaylist: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryDownloads: () -> Unit
) {
    val TAG = "StatsScreen"
    Log.d(TAG, "🖥️ Rendering StatsScreen - Expiry: $licenseExpiry, Items: ${playlist.items.size}")
    
    val focusRequester = remember { FocusRequester() }

    // Logic: Parse the license expiry once and derive display values
    val (statusText, daysLeft) = remember(licenseExpiry) {
        val result = StatsScreenLogic.processLicense(licenseExpiry)
        Log.d(TAG, "🗓️ Parsed License -> Display: ${result.first}, Days: ${result.second}")
        result
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(48.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Statistics",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Text(
                        text = "Playlist: ${playlist.name} (${playlist.code})",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = if (daysLeft != null) "License Status: $daysLeft days remaining" else "License Status: $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            daysLeft == null -> Color.Red
                            daysLeft < 7 -> Color.Red
                            else -> Color.Green
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isSocketConnected) Color.Green else Color.Red,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSocketConnected) "Remote Control: Connected" else "Remote Control: Disconnected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSocketConnected) Color.Green else Color.Red
                        )
                    }
                }

                Row {
                    Button(
                        onClick = onBackToPlaylist,
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Text("Back to Playlist")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF424242),
                            focusedContainerColor = Color(0xFF616161)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Settings")
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Red.copy(alpha = 0.7f),
                            focusedContainerColor = Color.Red
                        )
                    ) {
                        Text("Reset Registration")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Content Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                
                // Left Column: General Device Info (Scrollable)
                val leftColumnScrollState = rememberScrollState()
                val leftColumnScope = rememberCoroutineScope()
                val scrollAmount = 100f // pixels to scroll per D-pad press
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(leftColumnScrollState)
                        .padding(end = 16.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown, Key.NavigateNext -> {
                                        leftColumnScope.launch {
                                            leftColumnScrollState.animateScrollTo(
                                                (leftColumnScrollState.value + scrollAmount).toInt()
                                            )
                                        }
                                        true
                                    }
                                    Key.DirectionUp, Key.NavigatePrevious -> {
                                        leftColumnScope.launch {
                                            leftColumnScrollState.animateScrollTo(
                                                (leftColumnScrollState.value - scrollAmount).toInt()
                                            )
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusable()
                ) {
                    StatItem(
                        label = "Remote Management",
                        value = if (isSocketConnected) "Connected" else "Offline",
                        valueColor = if (isSocketConnected) Color.Green else Color.Red
                    )
                    StatItem(label = "Total Items", value = "${playlist.items.size}")
                    
                    // Playback Mode Section
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Playback Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val isFullyCached = cacheProgress >= 1.0f
                    val isPartiallyCached = cacheProgress > 0f && !isFullyCached
                    
                    StatItem(
                        label = "Mode",
                        value = when {
                            isFullyCached -> "Offline Mode"
                            isPartiallyCached -> "Hybrid Mode"
                            else -> "Streaming Mode"
                        },
                        valueColor = when {
                            isFullyCached -> Color.Green
                            isPartiallyCached -> Color(0xFFFFB300)
                            else -> Color(0xFF2196F3)
                        }
                    )
                    
                    Text(
                        text = when {
                            isFullyCached -> "All media is playing from local storage"
                            isPartiallyCached -> "Playing available files from storage, others streamed"
                            else -> "All media is streamed directly from the server"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    // Storage Information
                    storageStats?.let { stats ->
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Device Storage",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatItem(
                            label = "Available",
                            value = "${stats.availableBytes / 1024 / 1024} MB",
                            valueColor = when {
                                stats.availableBytes < 500L * 1024 * 1024 -> Color.Red
                                stats.availableBytes < 1024L * 1024 * 1024 -> Color(0xFFFF9800)
                                else -> Color.Green
                            }
                        )
                        
                        StatItem(
                            label = "Cache Size",
                            value = "${stats.cacheBytes / 1024 / 1024} MB"
                        )
                        
                        val usedPercentage = ((stats.totalBytes - stats.availableBytes) * 100 / stats.totalBytes).toInt()
                        StatItem(
                            label = "Storage Used",
                            value = "$usedPercentage%",
                            valueColor = when {
                                usedPercentage > 90 -> Color.Red
                                usedPercentage > 80 -> Color(0xFFFF9800)
                                else -> Color.White
                            }
                        )
                    }

                    // Error Section (Conditional)
                    playbackError?.let {
                        Spacer(modifier = Modifier.height(32.dp))
                        Surface(
                            modifier = Modifier.width(300.dp),
                            colors = SurfaceDefaults.colors(containerColor = Color.Red.copy(alpha = 0.2f)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Recent Playback Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Red
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Video: ${it.videoName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Reason: ${it.errorMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }

                // Right Column: Detailed Playlist Item List
                val rightColumnScrollState = rememberScrollState()
                val rightColumnScope = rememberCoroutineScope()
                
                Column(modifier = Modifier.weight(2f)) {
                    Text(
                        text = "Playlist Items",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        colors = SurfaceDefaults.colors(
                            containerColor = Color.DarkGray.copy(alpha = 0.3f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize()
                                .verticalScroll(rightColumnScrollState)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionDown, Key.NavigateNext -> {
                                                rightColumnScope.launch {
                                                    rightColumnScrollState.animateScrollTo(
                                                        (rightColumnScrollState.value + scrollAmount).toInt()
                                                    )
                                                }
                                                true
                                            }
                                            Key.DirectionUp, Key.NavigatePrevious -> {
                                                rightColumnScope.launch {
                                                    rightColumnScrollState.animateScrollTo(
                                                        (rightColumnScrollState.value - scrollAmount).toInt()
                                                    )
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusable()
                        ) {
                            playlist.items.forEach { item ->
                                val itemProgress = videoProgressList.find { it.itemId == item.id }
                                PlaylistItemRow(item, mediaCacheManager, cacheProgress, itemProgress)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.White) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
    }
}

@Composable
private fun PlaylistItemRowStreaming(item: PlaylistItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1565C0).copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Streaming indicator
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Streaming",
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "${item.order + 1}. ${item.video?.fileName ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Text(
                    text = "📡 Streaming from server",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2196F3)
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
            val type = if (item.video?.mimeType?.startsWith("video") == true) "Video" else "Image"
            Text(
                text = "$type | ${item.duration}s",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            item.video?.fileSize?.let { size ->
                Text(
                    text = "${size / 1024 / 1024} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: PlaylistItem, 
    mediaCacheManager: MediaCacheManager, 
    cacheProgress: Float,
    itemProgress: com.logicalvalley.digitalSignage.viewmodel.MainViewModel.VideoDownloadProgress?
) {
    // Recheck cache status whenever cacheProgress or itemProgress changes
    // This now validates file exists, is > 1KB, and is readable
    val localFile = remember(cacheProgress, itemProgress?.downloadedBytes, item.id) {
        mediaCacheManager.getLocalFile(item)
    }
    val isCached = localFile != null
    
    // Calculate download progress
    val downloadProgress = remember(itemProgress?.downloadedBytes, itemProgress?.fileSize) {
        itemProgress?.getProgress() ?: 0f
    }
    
    // Determine state: downloading (0-100%), validating (100% but not cached), or cached/not cached
    val isDownloading = !isCached && downloadProgress > 0f && downloadProgress < 1f
    val isValidating = !isCached && downloadProgress >= 1f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when {
                    isCached -> Color(0xFF1B5E20).copy(alpha = 0.2f)
                    isValidating -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                    isDownloading -> Color(0xFF1565C0).copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cache status indicator
                Icon(
                    imageVector = when {
                        isCached -> Icons.Default.CheckCircle
                        isValidating -> Icons.Default.CheckCircle
                        isDownloading -> Icons.Default.CloudDownload
                        else -> Icons.Default.CloudDownload
                    },
                    contentDescription = when {
                        isCached -> "Cached and validated"
                        isValidating -> "Validating"
                        isDownloading -> "Downloading"
                        else -> "Not cached"
                    },
                    tint = when {
                        isCached -> Color.Green
                        isValidating -> Color(0xFFFFB300)
                        isDownloading -> Color(0xFF2196F3)
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "${item.order + 1}. ${item.video?.fileName ?: "Unknown"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        text = when {
                            isCached -> "✓ Available offline (validated)"
                            isValidating -> "⏳ Validating file..."
                            isDownloading -> "⬇ Downloading... ${String.format("%.2f", downloadProgress * 100)}%"
                            else -> "⚠ Needs download"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isCached -> Color.Green
                            isValidating -> Color(0xFFFFB300)
                            isDownloading -> Color(0xFF2196F3)
                            else -> Color(0xFFFF9800)
                        }
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val type = if (item.video?.mimeType?.startsWith("video") == true) "Video" else "Image"
                Text(
                    text = "$type | ${item.duration}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                item.video?.fileSize?.let { size ->
                    when {
                        isDownloading -> {
                            val downloadedMB = (itemProgress?.downloadedBytes ?: 0L).toDouble() / 1024 / 1024
                            val totalMB = size.toDouble() / 1024 / 1024
                            Text(
                                text = "${String.format("%.2f", downloadedMB)} / ${String.format("%.2f", totalMB)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2196F3)
                            )
                        }
                        isValidating -> {
                            val fileMB = size.toDouble() / 1024 / 1024
                            Text(
                                text = "${String.format("%.2f", fileMB)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB300)
                            )
                        }
                        else -> {
                            val fileMB = size.toDouble() / 1024 / 1024
                            Text(
                                text = "${String.format("%.2f", fileMB)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
        
        // Show progress bar for downloading and validating files
        when {
            isDownloading -> {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFF2196F3),
                    trackColor = Color.DarkGray
                )
            }
            isValidating -> {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFFFFB300),
                    trackColor = Color.DarkGray
                )
            }
        }
    }
}
/**
 * Pure logic helper to handle data processing for the Stats Screen.
 */
private object StatsScreenLogic {
    fun processLicense(rawExpiry: String?): Pair<String, Long?> {
        if (rawExpiry.isNullOrEmpty() || rawExpiry == "null") {
            return "Unknown (Check Backend)" to null
        }
        if (rawExpiry == "API_MISSING_LICENSE") {
            return "Server Error: License key missing" to null
        }
        if (rawExpiry == "LICENSE_EXPIRY_NULL") {
            return "Server Error: Expiry date null" to null
        }

        val cleaned = rawExpiry.replace("\"", "").trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        var parsedDate: Date? = null
        for (format in formats) {
            try {
                parsedDate = SimpleDateFormat(format, Locale.US).apply {
                    if (format.contains("Z")) timeZone = TimeZone.getTimeZone("UTC")
                }.parse(cleaned)
                if (parsedDate != null) break
            } catch (e: Exception) {}
        }

        if (parsedDate == null) {
            return cleaned to null // Return raw string if parsing failed
        }

        // Format for display
        val displayStr = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(parsedDate)

        // Calculate days remaining
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val diff = parsedDate.time - today.time
        val days = if (diff > 0) TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) else 0L

        return displayStr to days
    }
}
