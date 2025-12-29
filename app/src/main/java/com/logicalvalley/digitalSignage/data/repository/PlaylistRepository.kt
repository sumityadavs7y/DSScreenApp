package com.logicalvalley.digitalSignage.data.repository

import android.util.Log
import com.google.gson.Gson
import com.logicalvalley.digitalSignage.data.api.ApiService
import com.logicalvalley.digitalSignage.data.model.DeviceRegisterRequest
import com.logicalvalley.digitalSignage.data.model.RegisterResponse
import com.logicalvalley.digitalSignage.data.model.TimelineResponse

class LicenseExpiredException(message: String, val response: RegisterResponse? = null) : Exception(message)
class TimelineLicenseExpiredException(message: String, val response: TimelineResponse? = null) : Exception(message)

class PlaylistRepository(private val apiService: ApiService) {
    private val gson = Gson()
    private val TAG = "PlaylistRepository"

    suspend fun registerDevice(playlistCode: String, uid: String): Result<RegisterResponse> {
        Log.d(TAG, "📡 Fetching Register API for code: $playlistCode, uid: $uid")
        return try {
            val response = apiService.registerDevice(DeviceRegisterRequest(playlistCode, uid))
            Log.d(TAG, "📥 Register API Response Code: ${response.code()}")
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "✅ Register API Success Body: ${gson.toJson(body)}")
                body?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else if (response.code() == 403) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "🚫 Register License Expired Error Body: $errorBody")
                val registerResponse = try {
                    gson.fromJson(errorBody, RegisterResponse::class.java)
                } catch (e: Exception) {
                    null
                }
                Result.failure(LicenseExpiredException(response.message(), registerResponse))
            } else {
                Log.e(TAG, "❌ Register API Error: ${response.code()} ${response.message()}")
                Result.failure(Exception("Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception during Register API call", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistTimeline(playlistId: String): Result<TimelineResponse> {
        Log.d(TAG, "📡 Fetching Timeline API for id: $playlistId")
        return try {
            val response = apiService.getPlaylistTimeline(playlistId)
            Log.d(TAG, "📥 Timeline API Response Code: ${response.code()}")
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "✅ Timeline API Success Body: ${gson.toJson(body)}")
                body?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else if (response.code() == 403) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "🚫 Timeline License Expired Error Body: $errorBody")
                val timelineResponse = try {
                    gson.fromJson(errorBody, TimelineResponse::class.java)
                } catch (e: Exception) {
                    null
                }
                Result.failure(TimelineLicenseExpiredException(response.message(), timelineResponse))
            } else {
                Log.e(TAG, "❌ Timeline API Error: ${response.code()} ${response.message()}")
                Result.failure(Exception("Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception during Timeline API call", e)
            Result.failure(e)
        }
    }
}
