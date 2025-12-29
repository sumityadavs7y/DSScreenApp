package com.logicalvalley.digitalSignage.data.api

import com.logicalvalley.digitalSignage.data.model.DeviceRegisterRequest
import com.logicalvalley.digitalSignage.data.model.RegisterResponse
import com.logicalvalley.digitalSignage.data.model.TimelineResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("playlists/device/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): Response<RegisterResponse>

    @GET("api/playlists/{playlistId}/timeline")
    suspend fun getPlaylistTimeline(@Path("playlistId") playlistId: String): Response<TimelineResponse>
}

