package com.example.dsscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.dsscreen.navigation.NavGraph
import com.example.dsscreen.ui.theme.DSScreenTheme
import com.example.dsscreen.viewmodel.CacheViewModel
import com.example.dsscreen.viewmodel.DeviceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DSScreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel = DeviceViewModel(applicationContext)
                    val cacheViewModel = CacheViewModel(applicationContext)
                    
                    NavGraph(
                        navController = navController,
                        viewModel = viewModel,
                        cacheViewModel = cacheViewModel
                    )
                }
            }
        }
    }
}