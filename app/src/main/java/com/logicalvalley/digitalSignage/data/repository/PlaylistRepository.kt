package com.logicalvalley.digitalSignage.data.repository

import android.util.Log
import com.google.gson.Gson
import com.logicalvalley.digitalSignage.data.api.ApiService
import com.logicalvalley.digitalSignage.data.model.*

class LicenseExpiredException(message: String, val response: RegisterResponse? = null) : Exception(message)
class TimelineLicenseExpiredException(message: String, val response: TimelineResponse? = null) : Exception(message)
class DeviceDeregisteredException(message: String) : Exception(message)

class PlaylistRepository(private val apiService: ApiService) {
    private val gson = Gson()
    private val TAG = "PlaylistRepository"

    suspend fun initRegistration(deviceId: String): Result<InitRegistrationResponse> {
        Log.d(TAG, "📡 Initializing registration for device: $deviceId")
        return try {
            val response = apiService.initRegistration(mapOf("deviceId" to deviceId))
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "✅ Init Registration Success: ${gson.toJson(body)}")
                body?.let { Result.success(it) } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMsg = "Error: ${response.code()} ${response.message()}"
                Log.e(TAG, "❌ Init Registration API Error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception during Init Registration API call: ${e.message}", e)
            Result.failure(e)
        }
    }

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

    suspend fun getPlaylistTimeline(playlistId: String, deviceUID: String? = null): Result<TimelineResponse> {
        Log.d(TAG, "📡 Fetching Timeline API for id: $playlistId, device: $deviceUID")
        return try {
            val response = apiService.getPlaylistTimeline(playlistId, deviceUID)
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
            } else if (response.code() == 410) {
                Log.w(TAG, "🚫 Device deregistered (410 Gone)")
                Result.failure(DeviceDeregisteredException("Device has been deregistered"))
            } else {
                Log.e(TAG, "❌ Timeline API Error: ${response.code()} ${response.message()}")
                Result.failure(Exception("Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception during Timeline API call", e)
            Result.failure(e)
        }
    }

    suspend fun deregisterDevice(uid: String): Result<Boolean> {
        Log.d(TAG, "📡 Deregistering device from server: $uid")
        return try {
            val response = apiService.deregisterDevice(uid)
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Deregistration API Success")
                Result.success(true)
            } else {
                Log.e(TAG, "❌ Deregistration API Error: ${response.code()}")
                Result.failure(Exception("Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception during Deregistration API call", e)
            Result.failure(e)
        }
    }
}
