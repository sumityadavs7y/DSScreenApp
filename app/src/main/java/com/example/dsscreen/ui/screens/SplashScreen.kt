package com.example.dsscreen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsscreen.viewmodel.DeviceViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: DeviceViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToRegistration: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val isRegistered by viewModel.isRegistered.collectAsState(initial = false)
    val playlist by viewModel.playlist.collectAsState()
    
    // Minimum splash screen duration
    var minDurationPassed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(1500) // Show splash for at least 1.5 seconds
        minDurationPassed = true
    }
    
    // Navigate when loading is complete and minimum duration has passed
    LaunchedEffect(isLoading, minDurationPassed, isRegistered, playlist) {
        if (!isLoading && minDurationPassed) {
            if (isRegistered && playlist != null && playlist!!.items?.isNotEmpty() == true) {
                onNavigateToPlayer()
            } else {
                onNavigateToRegistration()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // Company Logo - You can replace this with your actual logo
            // For now, showing a placeholder
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DS",
                    fontSize = 72.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "Digital Signage",
                fontSize = 28.sp,
                color = Color.White,
                fontWeight = FontWeight.Light
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
    }
}

