package com.example.dsscreen.data.model

import com.google.gson.annotations.SerializedName

/**
 * Playlist data model
 */
data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val code: String,
    @SerializedName("isActive")
    val isActive: Boolean = true,
    val items: List<PlaylistItem>? = null
)

/**
 * Playlist item data model
 */
data class PlaylistItem(
    val id: String,
    @SerializedName("playlistId")
    val playlistId: String,
    @SerializedName("videoId")
    val videoId: String,
    val order: Int,
    val duration: Int, // Duration in seconds
    val video: PlaylistVideo? = null
)

/**
 * Video data in playlist
 */
data class PlaylistVideo(
    val id: String,
    val fileName: String,
    val filePath: String? = null,
    val thumbnailPath: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val duration: Int? = null,
    val resolution: String? = null
)

