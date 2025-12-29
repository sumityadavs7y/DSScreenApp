package com.logicalvalley.digitalSignage.ui.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onRegister: (String) -> Unit,
    error: String? = null
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter Playlist Code",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.width(350.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { 
                    if (it.length <= 5) {
                        code = it
                        if (code.length == 5) {
                            onRegister(code)
                        }
                    }
                },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                singleLine = true,
                placeholder = {
                    Text(
                        "_____",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 12.sp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (code.length == 5) onRegister(code) },
            enabled = code.length == 5
        ) {
            Text("Register Device")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Device will auto-register after 5 characters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
