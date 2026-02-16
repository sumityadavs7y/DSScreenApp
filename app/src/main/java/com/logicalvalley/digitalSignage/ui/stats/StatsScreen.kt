package com.logicalvalley.digitalSignage.ui.stats

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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

import androidx.compose.ui.res.painterResource
import com.logicalvalley.digitalSignage.R
import com.logicalvalley.digitalSignage.data.local.MediaCacheManager
import com.logicalvalley.digitalSignage.viewmodel.MainViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

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
    videoProgressList: List<MainViewModel.VideoDownloadProgress>,
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
                    
                    // Show retry button only when there are failed downloads AND retry is not in progress
                    if (failedDownloadCount > 0 && !isRetrying) {
                        Button(
                            onClick = onRetryDownloads,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFFFF9800),
                                focusedContainerColor = Color(0xFFFFA726)
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔄 Retry Downloads ($failedDownloadCount)")
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
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
                    StatItem(
                        label = "Offline Ready", 
                        value = "${(cacheProgress * 100).toInt()}%",
                        valueColor = when {
                            cacheProgress >= 1.0f -> Color.Green
                            cacheProgress > 0.5f -> Color(0xFF64B5F6)
                            else -> Color(0xFFFF9800)
                        }
                    )
                    
                    // Segmented Download Progress Bar
                    if (videoProgressList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Download Progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedProgressBar(
                            videoProgressList = videoProgressList,
                            modifier = Modifier.width(300.dp).height(24.dp)
                        )
                    }
                    
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
                        
                        StatItem(
                            label = "Storage Used",
                            value = "${stats.usedPercentage}%",
                            valueColor = when {
                                stats.usedPercentage > 90 -> Color.Red
                                stats.usedPercentage > 80 -> Color(0xFFFF9800)
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
                                PlaylistItemRow(item)
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
private fun PlaylistItemRow(item: PlaylistItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val type = if (item.video?.mimeType?.startsWith("video") == true) "Video" else "Image"
        Text(
            text = "${item.order + 1}. ${item.video?.fileName ?: "Unknown"}",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$type | ${item.duration}s",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}


/**
 * Segmented Progress Bar - shows download progress per video
 * Each segment width is proportional to video file size
 * Segments fill from left to right like traditional progress bars
 * Colors: Grey (empty) -> Orange (filling) -> Green (complete)
 */
@Composable
private fun SegmentedProgressBar(
    videoProgressList: List<MainViewModel.VideoDownloadProgress>,
    modifier: Modifier = Modifier
) {
    val totalSize = videoProgressList.sumOf { it.fileSize }.toFloat()
    
    if (totalSize <= 0f) {
        // Fallback if no size data
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )
        return
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
    ) {
        videoProgressList.forEachIndexed { index, video ->
            // Each segment with traditional left-to-right fill
            val widthFraction = (video.fileSize.toFloat() / totalSize)
            val progress = video.getProgress()
            
            // Background color (empty/unfilled portion)
            val backgroundColor = Color(0xFF424242)  // Grey
            
            // Foreground color (filled portion) based on progress
            val foregroundColor = when {
                progress >= 1.0f -> Color(0xFF4CAF50)  // Green - complete
                progress > 0f -> Color(0xFFFF9800)      // Orange - in progress
                else -> backgroundColor                  // Same as background when not started
            }
            
            Box(
                modifier = Modifier
                    .weight(widthFraction)
                    .fillMaxHeight()
                    .background(backgroundColor)
            ) {
                // Filled portion - grows from left to right
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(foregroundColor)
                )
            }
            
            // Add vertical divider between segments (not after last one)
            if (index < videoProgressList.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.6f))
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
