package com.logicalvalley.digitalSignage.data.model

import com.google.gson.annotations.SerializedName

data class DeviceRegisterRequest(
    @SerializedName("playlistCode") val playlistCode: String,
    @SerializedName("uid") val uid: String,
    @SerializedName("deviceInfo") val deviceInfo: Map<String, Any> = emptyMap()
)

data class RegisterResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: RegisterData?,
    @SerializedName("license") val topLevelLicense: LicenseInfo?,
    @SerializedName("playlist") val topLevelPlaylist: Playlist?, // For Socket.io events
    @SerializedName("device") val topLevelDevice: DeviceInfo?    // For Socket.io events
)

data class TimelineResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val items: List<PlaylistItem>?,
    @SerializedName("license") val license: LicenseInfo?,
    @SerializedName("deviceDeleted") val deviceDeleted: Boolean? = false
)

data class InitRegistrationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: InitRegistrationData?
)

data class InitRegistrationData(
    @SerializedName("sessionToken") val sessionToken: String,
    @SerializedName("qrCodeDataUrl") val qrCodeDataUrl: String,
    @SerializedName("registrationUrl") val registrationUrl: String,
    @SerializedName("expiresAt") val expiresAt: String
)

data class RegisterData(
    @SerializedName("device") val device: DeviceInfo?,
    @SerializedName("playlist") val playlist: Playlist?,
    @SerializedName("license") val license: LicenseInfo?
)

data class DeviceInfo(
    @SerializedName("id") val id: String?,
    @SerializedName("uid") val uid: String?
)

data class Playlist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("code") val code: String,
    @SerializedName("items") val items: List<PlaylistItem>
)

data class PlaylistItem(
    @SerializedName("id") val id: String,
    @SerializedName("order") val order: Int,
    @SerializedName("duration") val duration: Int,
    @SerializedName("video") val video: MediaVideo?
)

data class MediaVideo(
    @SerializedName("id") val id: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("duration") val duration: Float?,
    @SerializedName("fileSize") val fileSize: Long?
)

data class LicenseInfo(
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("expires_at") val expiresAtSnake: String?, // Fallback for snake_case
    @SerializedName("isActive") val isActive: Boolean?
)

data class PlaybackErrorInfo(
    val videoName: String,
    val errorMessage: String,
    val timestamp: java.util.Date
)
