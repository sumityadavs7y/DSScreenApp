package com.example.dsscreen.player

import com.example.dsscreen.data.model.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages playlist playback state and sequencing
 */
class PlaylistManager(
    private val items: List<PlaylistItem>
) {
    private var currentIndex = 0
    private val _currentItem = MutableStateFlow<PlaylistItem?>(null)
    val currentItem: StateFlow<PlaylistItem?> = _currentItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        if (items.isNotEmpty()) {
            _currentItem.value = items[0]
        }
    }

    /**
     * Get the current video URL
     * The download endpoint is public and supports range requests for streaming
     */
    fun getCurrentVideoUrl(baseUrl: String): String? {
        return currentItem.value?.video?.let { video ->
            // Construct the video download URL (public endpoint)
            "${baseUrl}api/videos/${video.id}/download"
        }
    }

    /**
     * Get the current video duration (in seconds) for looping
     */
    fun getCurrentDuration(): Int {
        return currentItem.value?.duration ?: 0
    }

    /**
     * Get the current video file name
     */
    fun getCurrentVideoName(): String {
        return currentItem.value?.video?.fileName ?: "Unknown"
    }

    /**
     * Move to the next video in the playlist
     * Loops back to the first video when reaching the end
     */
    fun moveToNext() {
        if (items.isEmpty()) return

        currentIndex = (currentIndex + 1) % items.size
        _currentItem.value = items[currentIndex]
    }

    /**
     * Reset to the first video
     */
    fun reset() {
        if (items.isNotEmpty()) {
            currentIndex = 0
            _currentItem.value = items[0]
        }
    }

    /**
     * Get total number of videos in playlist
     */
    fun getTotalVideos(): Int = items.size

    /**
     * Get current video index (0-based)
     */
    fun getCurrentIndex(): Int = currentIndex

    /**
     * Start playing
     */
    fun play() {
        _isPlaying.value = true
    }

    /**
     * Pause playing
     */
    fun pause() {
        _isPlaying.value = false
    }

    /**
     * Stop and reset
     */
    fun stop() {
        _isPlaying.value = false
        reset()
    }
}

