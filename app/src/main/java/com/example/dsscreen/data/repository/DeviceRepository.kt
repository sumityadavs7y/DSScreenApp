package com.example.dsscreen.data.repository

import com.example.dsscreen.data.api.RetrofitInstance
import com.example.dsscreen.data.model.DeviceInfo
import com.example.dsscreen.data.model.DeviceRegistrationRequest
import com.example.dsscreen.data.model.DeviceRegistrationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for device-related operations
 */
class DeviceRepository {
    private val deviceApi = RetrofitInstance.deviceApi

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
                val errorMessage = response.body()?.message
                    ?: response.errorBody()?.string()
                    ?: "Registration failed"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

