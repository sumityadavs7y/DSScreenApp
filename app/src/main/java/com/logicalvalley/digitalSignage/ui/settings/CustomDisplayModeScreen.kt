package com.logicalvalley.digitalSignage.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.logicalvalley.digitalSignage.data.model.Playlist
import com.logicalvalley.digitalSignage.data.model.PlaylistItem

/**
 * Custom Display Mode Screen - Set display mode per playlist item
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomDisplayModeScreen(
    playlist: Playlist,
    customDisplayModes: Map<String, String>,
    onBack: () -> Unit,
    onSetItemDisplayMode: (String, String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

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
                        text = "Custom Display Modes",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Text(
                        text = "Set display mode for each item",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text("Back")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Playlist Items List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlist.items) { item ->
                    PlaylistItemDisplayMode(
                        item = item,
                        currentMode = customDisplayModes[item.id] ?: "FIT",
                        onSetMode = { mode -> onSetItemDisplayMode(item.id, mode) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistItemDisplayMode(
    item: PlaylistItem,
    currentMode: String,
    onSetMode: (String) -> Unit
) {
    val isVideo = item.video?.mimeType?.startsWith("video") == true
    val fileName = item.video?.fileName ?: "Unknown"
    val duration = "${item.duration}s"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = SurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.VideoLibrary else Icons.Default.Image,
                    contentDescription = null,
                    tint = if (isVideo) Color(0xFF2196F3) else Color(0xFF9C27B0),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Display Mode Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FIT Button
                Button(
                    onClick = { onSetMode("FIT") },
                    modifier = Modifier.size(width = 90.dp, height = 50.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = if (currentMode == "FIT")
                            Color(0xFF9C27B0) else Color(0xFF424242),
                        focusedContainerColor = if (currentMode == "FIT")
                            Color(0xFFAB47BC) else Color(0xFF616161)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "FIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // FILL Button
                Button(
                    onClick = { onSetMode("FILL") },
                    modifier = Modifier.size(width = 90.dp, height = 50.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = if (currentMode == "FILL")
                            Color(0xFF9C27B0) else Color(0xFF424242),
                        focusedContainerColor = if (currentMode == "FILL")
                            Color(0xFFAB47BC) else Color(0xFF616161)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "FILL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // STRETCH Button
                Button(
                    onClick = { onSetMode("STRETCH") },
                    modifier = Modifier.size(width = 90.dp, height = 50.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = if (currentMode == "STRETCH")
                            Color(0xFF9C27B0) else Color(0xFF424242),
                        focusedContainerColor = if (currentMode == "STRETCH")
                            Color(0xFFAB47BC) else Color(0xFF616161)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "STRETCH",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

