package com.logicalvalley.digitalSignage.ui.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import coil.compose.AsyncImage

import androidx.compose.ui.graphics.Color
import com.logicalvalley.digitalSignage.data.model.InitRegistrationData
import android.util.Log
import androidx.compose.material.icons.filled.Warning
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material.icons.Icons

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onRegister: (String) -> Unit,
    onRefreshQr: () -> Unit = {},
    qrData: InitRegistrationData? = null,
    error: String? = null
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(true) }

    // Decode Base64 QR code to Bitmap for more reliable rendering
    val qrBitmap = remember(qrData?.qrCodeDataUrl) {
        qrData?.let { 
            Log.d("RegistrationScreen", "🔗 QR representation URL: ${it.registrationUrl}")
        }
        qrData?.qrCodeDataUrl?.let { dataUrl ->
            try {
                if (dataUrl.startsWith("data:image")) {
                    val base64String = dataUrl.substringAfter(",")
                    val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("RegistrationScreen", "❌ Base64 Decode Failed", e)
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (showQr) "Scan QR to Register" else "Enter Playlist Code",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        if (showQr) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                if (qrData != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(qrBitmap)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Registration QR Code",
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        error = {
                            val errorMsg = it.result.throwable.message ?: "Unknown Error"
                            Log.e("RegistrationScreen", "❌ QR Image Load Failed: $errorMsg")
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Load Failed",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        onSuccess = { Log.d("RegistrationScreen", "✅ QR Image Rendered Successfully") }
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            
            if (qrData == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Initializing session...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scan this code with your phone to register",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
            }
        } else {
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

        Row {
            Button(
                onClick = { showQr = !showQr }
            ) {
                Text(if (showQr) "Use Code Instead" else "Use QR Instead")
            }
            
            if (showQr) {
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onRefreshQr) {
                    Text("Refresh QR")
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { if (code.length == 5) onRegister(code) },
                    enabled = code.length == 5
                ) {
                    Text("Register Device")
                }
            }
        }
        
        if (!showQr) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Device will auto-register after 5 characters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
