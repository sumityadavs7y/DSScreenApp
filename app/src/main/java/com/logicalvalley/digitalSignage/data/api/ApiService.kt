package com.logicalvalley.digitalSignage.data.api

import com.logicalvalley.digitalSignage.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("playlists/device/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): Response<RegisterResponse>

    @GET("api/playlists/{playlistId}/timeline")
    suspend fun getPlaylistTimeline(
        @Path("playlistId") playlistId: String,
        @Query("deviceUID") deviceUID: String? = null
    ): Response<TimelineResponse>

    @POST("api/device/init-registration")
    suspend fun initRegistration(@Body body: Map<String, String>): Response<InitRegistrationResponse>

    @DELETE("api/device/deregister/{uid}")
    suspend fun deregisterDevice(@Path("uid") uid: String): Response<Map<String, Any>>
}

