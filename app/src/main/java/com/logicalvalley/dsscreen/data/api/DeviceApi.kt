package com.logicalvalley.dsscreen.data.api

import com.logicalvalley.dsscreen.data.model.ApiResponse
import com.logicalvalley.dsscreen.data.model.DeviceRegistrationRequest
import com.logicalvalley.dsscreen.data.model.DeviceRegistrationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Device API endpoints
 */
interface DeviceApi {
    /**
     * Register a device to a playlist using playlist code
     * PUBLIC ENDPOINT - No authentication required
     */
    @POST("/playlists/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<ApiResponse<DeviceRegistrationResponse>>
}

