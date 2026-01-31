package com.logicalvalley.digitalSignage.ui.stats

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    currentRotation: String,
    onBackToPlaylist: () -> Unit,
    onReset: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateAntiClockwise: () -> Unit,
    onSetAutoRotation: () -> Unit
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 16.dp)
                ) {
                    StatItem(
                        label = "Remote Management",
                        value = if (isSocketConnected) "Connected" else "Offline",
                        valueColor = if (isSocketConnected) Color.Green else Color.Red
                    )
                    StatItem(label = "Total Items", value = "${playlist.items.size}")
                    StatItem(
                        label = "Offline Ready", 
                        value = "${(cacheProgress * 100).toInt()}%"
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LinearProgressIndicator(
                        progress = { cacheProgress },
                        modifier = Modifier.width(200.dp).height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Screen Rotation Section
                    Surface(
                        modifier = Modifier.width(350.dp),
                        shape = RectangleShape,
                        colors = SurfaceDefaults.colors(
                            containerColor = Color(0xFF1E1E1E)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // Section Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Screen Rotation",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Current Rotation Display
                            Text(
                                text = getRotationLabel(currentRotation),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Rotation Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Clockwise Button
                                Button(
                                    onClick = onRotateClockwise,
                                    modifier = Modifier.weight(1f).height(60.dp),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color(0xFF2196F3),
                                        focusedContainerColor = Color(0xFF42A5F5)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RotateRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "CW",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                                
                                // Anti-Clockwise Button
                                Button(
                                    onClick = onRotateAntiClockwise,
                                    modifier = Modifier.weight(1f).height(60.dp),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color(0xFF2196F3),
                                        focusedContainerColor = Color(0xFF42A5F5)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RotateLeft,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "CCW",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                                
                                // Auto Button
                                Button(
                                    onClick = onSetAutoRotation,
                                    modifier = Modifier.weight(1f).height(60.dp),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (currentRotation == "AUTO") 
                                            Color(0xFF4CAF50) else Color(0xFF424242),
                                        focusedContainerColor = if (currentRotation == "AUTO") 
                                            Color(0xFF66BB6A) else Color(0xFF616161)
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "AUTO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
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
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(playlist.items) { item ->
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

private fun getRotationLabel(rotation: String): String {
    return when (rotation) {
        "AUTO" -> "Auto (Device Setting)"
        "0" -> "Portrait (0°)"
        "90" -> "Landscape (90°)"
        "180" -> "Reverse Portrait (180°)"
        "270" -> "Reverse Landscape (270°)"
        else -> "Unknown"
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
