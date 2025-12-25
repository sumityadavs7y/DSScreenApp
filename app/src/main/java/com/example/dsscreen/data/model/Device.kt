package com.example.dsscreen.data.model

import com.google.gson.annotations.SerializedName

/**
 * Device data model
 */
data class Device(
    val id: String,
    val uid: String,
    @SerializedName("lastSeen")
    val lastSeen: String? = null,
    @SerializedName("isActive")
    val isActive: Boolean = true
)

/**
 * Device registration request
 */
data class DeviceRegistrationRequest(
    val playlistCode: String,
    val uid: String,
    val deviceInfo: DeviceInfo? = null
)

/**
 * Device information collected from the Android device
 */
data class DeviceInfo(
    val resolution: String,
    val deviceModel: String,
    val androidVersion: String,
    val manufacturer: String,
    val brand: String,
    val timestamp: String,
    val location: String? = null
)

/**
 * Device registration response
 */
data class DeviceRegistrationResponse(
    val device: Device,
    val playlist: Playlist
)

