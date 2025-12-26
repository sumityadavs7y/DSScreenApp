package com.example.dsscreen.data.repository

import android.util.Log
import com.example.dsscreen.data.api.RetrofitInstance
import com.example.dsscreen.data.model.ApiResponse
import com.example.dsscreen.data.model.DeviceInfo
import com.example.dsscreen.data.model.DeviceRegistrationRequest
import com.example.dsscreen.data.model.DeviceRegistrationResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for device-related operations
 */
class DeviceRepository {
    private val deviceApi = RetrofitInstance.deviceApi
    private val gson = Gson()
    private val TAG = "DeviceRepository"

    /**
     * Register device to a playlist using playlist code
     */
    suspend fun registerDevice(
        playlistCode: String,
        uid: String,
        deviceInfo: DeviceInfo
    ): Result<DeviceRegistrationResponse> = withContext(Dispatchers.IO) {
        try {
            val request = DeviceRegistrationRequest(
                playlistCode = playlistCode,
                uid = uid,
                deviceInfo = deviceInfo
            )

            val response = deviceApi.registerDevice(request)

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("No data received"))
            } else {
                // Extract error message from response
                val errorMessage = when {
                    // First, try to get message from response body
                    response.body()?.message != null -> {
                        Log.w(TAG, "Registration failed: ${response.body()?.message}")
                        response.body()?.message!!
                    }
                    // Then try to parse error body as JSON
                    response.errorBody() != null -> {
                        try {
                            val errorBodyString = response.errorBody()?.string()
                            Log.w(TAG, "Error body: $errorBodyString")
                            
                            // Try to parse as ApiResponse
                            val errorResponse = gson.fromJson(
                                errorBodyString,
                                ApiResponse::class.java
                            )
                            errorResponse.message ?: errorResponse.error ?: errorBodyString ?: "Registration failed"
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse error body", e)
                            "Registration failed: ${response.message()}"
                        }
                    }
                    // Fallback to HTTP status message
                    else -> {
                        Log.w(TAG, "Registration failed: ${response.message()}")
                        "Registration failed: ${response.message()}"
                    }
                }
                
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            Result.failure(e)
        }
    }
}

