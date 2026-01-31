package com.logicalvalley.digitalSignage.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
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

/**
 * Settings Screen for rotation and display mode controls
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentRotation: String,
    currentDisplayMode: String,
    onBack: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateAntiClockwise: () -> Unit,
    onSetAutoRotation: () -> Unit,
    onSetDisplayMode: (String) -> Unit,
    onOpenCustomDisplayMode: () -> Unit
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
                        text = "Settings",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Text(
                        text = "Display & Rotation Controls",
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

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Screen Rotation Section
                Surface(
                    modifier = Modifier.width(500.dp),
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
                                imageVector = Icons.Default.ScreenRotation,
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

                        // Current Rotation
                        Text(
                            text = getRotationLabel(currentRotation),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Rotation Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Clockwise Button
                            Button(
                                onClick = onRotateClockwise,
                                modifier = Modifier.weight(1f).height(70.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF1976D2),
                                    focusedContainerColor = Color(0xFF2196F3)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Clockwise",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            // Anti-Clockwise Button
                            Button(
                                onClick = onRotateAntiClockwise,
                                modifier = Modifier.weight(1f).height(70.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF1976D2),
                                    focusedContainerColor = Color(0xFF2196F3)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateLeft,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Anti-Clockwise",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            // Auto Button
                            Button(
                                onClick = onSetAutoRotation,
                                modifier = Modifier.weight(1f).height(70.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (currentRotation == "AUTO")
                                        Color(0xFF388E3C) else Color(0xFF424242),
                                    focusedContainerColor = if (currentRotation == "AUTO")
                                        Color(0xFF4CAF50) else Color(0xFF616161)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Auto",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Display Mode Section
                Surface(
                    modifier = Modifier.width(500.dp),
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
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Display Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Current Display Mode
                        Text(
                            text = getDisplayModeLabel(currentDisplayMode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Display Mode Buttons - Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Fit Button
                            Button(
                                onClick = { onSetDisplayMode("FIT") },
                                modifier = Modifier.weight(1f).height(70.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (currentDisplayMode == "FIT")
                                        Color(0xFF9C27B0) else Color(0xFF424242),
                                    focusedContainerColor = if (currentDisplayMode == "FIT")
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
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "FIT",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            // Fill Button
                            Button(
                                onClick = { onSetDisplayMode("FILL") },
                                modifier = Modifier.weight(1f).height(70.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (currentDisplayMode == "FILL")
                                        Color(0xFF9C27B0) else Color(0xFF424242),
                                    focusedContainerColor = if (currentDisplayMode == "FILL")
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
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "FILL",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            // Stretch Button
                            Button(
                                onClick = { onSetDisplayMode("STRETCH") },
                                modifier = Modifier.weight(1f).height(70.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (currentDisplayMode == "STRETCH")
                                        Color(0xFF9C27B0) else Color(0xFF424242),
                                    focusedContainerColor = if (currentDisplayMode == "STRETCH")
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
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "STRETCH",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display Mode Buttons - Row 2 (Custom)
                        Button(
                            onClick = onOpenCustomDisplayMode,
                            modifier = Modifier.fillMaxWidth().height(70.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (currentDisplayMode == "CUSTOM")
                                    Color(0xFFFF6F00) else Color(0xFF424242),
                                focusedContainerColor = if (currentDisplayMode == "CUSTOM")
                                    Color(0xFFFF8F00) else Color(0xFF616161)
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "CUSTOM - Per Item Settings",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
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

private fun getDisplayModeLabel(mode: String): String {
    return when (mode) {
        "FIT" -> "Fit - Keep aspect ratio"
        "FILL" -> "Fill - Crop to fill screen"
        "STRETCH" -> "Stretch - Ignore aspect ratio"
        else -> "Unknown"
    }
}

