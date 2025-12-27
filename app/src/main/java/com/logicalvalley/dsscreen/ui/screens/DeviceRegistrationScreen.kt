package com.logicalvalley.dsscreen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logicalvalley.dsscreen.viewmodel.DeviceViewModel
import com.logicalvalley.dsscreen.viewmodel.RegistrationState

@Composable
fun DeviceRegistrationScreen(
    viewModel: DeviceViewModel,
    onRegistrationSuccess: () -> Unit
) {
    val registrationState by viewModel.registrationState.collectAsState()
    
    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.Success) {
            onRegistrationSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (registrationState) {
            is RegistrationState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "Registering...",
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
            
            is RegistrationState.Error -> {
                val errorMessage = (registrationState as RegistrationState.Error).message
                val isLicenseError = errorMessage.contains("license", ignoreCase = true) || 
                                    errorMessage.contains("expired", ignoreCase = true) ||
                                    errorMessage.contains("not active", ignoreCase = true)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(48.dp)
                ) {
                    // Error Icon
                    Text(
                        text = if (isLicenseError) "⚠️" else "❌",
                        fontSize = 64.sp
                    )
                    
                    // Error Title
                    Text(
                        text = if (isLicenseError) "License Issue" else "Registration Failed",
                        fontSize = 32.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Error Message Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentHeight(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E1E1E)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                fontSize = 20.sp,
                                color = Color(0xFFFF6B6B),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 28.sp
                            )
                            
                            if (isLicenseError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Please contact your administrator to resolve this issue or mail support@logicalvalley.in",
                                    fontSize = 16.sp,
                                    color = Color(0xFFCCCCCC),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Try Again Button
                    Button(
                        onClick = { viewModel.resetState() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Try Again",
                            fontSize = 20.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            else -> {
                var code by remember { mutableStateOf("") }
                
                LaunchedEffect(code) {
                    if (code.length == 5) {
                        viewModel.registerDevice(code)
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.padding(48.dp)
                ) {
                    Text(
                        text = "Enter Playlist Code",
                        fontSize = 32.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Light
                    )
                    
                    BasicTextField(
                        value = code,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isLetterOrDigit() }.take(5)
                            code = filtered
                        },
                        textStyle = TextStyle(
                            fontSize = 40.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .width(300.dp)
                            .border(
                                width = 2.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(24.dp),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (code.isEmpty()) {
                                    Text(
                                        text = "XXXXX",
                                        fontSize = 40.sp,
                                        color = Color(0xFF444444),
                                        letterSpacing = 8.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    if (code.isNotEmpty()) {
                        TextButton(onClick = { code = "" }) {
                            Text(
                                text = "Clear",
                                color = Color(0xFF888888),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
