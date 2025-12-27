package com.logicalvalley.dsscreen.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.logicalvalley.dsscreen.transcoding.TranscodingJob
import com.logicalvalley.dsscreen.transcoding.VideoTranscodingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing video transcoding operations
 */
class TranscodingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "TranscodingViewModel"
    private val transcodingManager = VideoTranscodingManager(application)
    
    // Track videos that have been successfully transcoded
    private val _transcodedVideos = MutableStateFlow<Set<String>>(emptySet())
    val transcodedVideos: StateFlow<Set<String>> = _transcodedVideos.asStateFlow()
    
    // Transcoding jobs state (from manager)
    val transcodingJobs: StateFlow<Map<String, TranscodingJob>> = transcodingManager.transcodingJobs
    
    // Track which videos are queued for transcoding
    private val _pendingQueue = MutableStateFlow<Set<String>>(emptySet())
    
    /**
     * Check if video has transcoded version
     */
    fun hasTranscodedVersion(videoId: String): Boolean {
        return transcodingManager.hasTranscodedVersion(videoId)
    }
    
    /**
     * Get transcoded video path (or null if not available)
     */
    fun getTranscodedVideoPath(videoId: String): String? {
        return transcodingManager.getTranscodedVideoPath(videoId)
    }
    
    /**
     * Request transcoding for a video
     * Returns true if transcoding started, false if already exists or in progress
     */
    fun requestTranscoding(videoId: String, sourceVideoPath: String): Boolean {
        viewModelScope.launch {
            // Check if already transcoded
            if (hasTranscodedVersion(videoId)) {
                Log.d(TAG, "Video $videoId already has transcoded version")
                _transcodedVideos.value = _transcodedVideos.value + videoId
                return@launch
            }
            
            // Check if already in queue or transcoding
            if (_pendingQueue.value.contains(videoId) || transcodingJobs.value.containsKey(videoId)) {
                Log.d(TAG, "Video $videoId already in transcode queue/processing")
                return@launch
            }
            
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🎬 Requesting transcoding for video: $videoId")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            _pendingQueue.value = _pendingQueue.value + videoId
            
            // Start transcoding
            transcodingManager.transcodeVideo(
                videoId = videoId,
                sourceVideoPath = sourceVideoPath,
                onProgress = { progress ->
                    // Progress updates are handled by the manager's flow
                    if (progress == 1f) {
                        Log.d(TAG, "✅ Transcoding complete for $videoId")
                    }
                },
                onComplete = { success, outputPath ->
                    viewModelScope.launch {
                        _pendingQueue.value = _pendingQueue.value - videoId
                        
                        if (success && outputPath != null) {
                            Log.d(TAG, "✅ Video $videoId transcoded successfully")
                            Log.d(TAG, "   Output: $outputPath")
                            _transcodedVideos.value = _transcodedVideos.value + videoId
                        } else {
                            Log.e(TAG, "❌ Video $videoId transcoding failed")
                        }
                    }
                }
            )
        }
        return true
    }
    
    /**
     * Get device optimal resolution
     */
    fun getDeviceOptimalResolution(): Pair<Int, Int> {
        return transcodingManager.getDeviceOptimalResolution()
    }
    
    /**
     * Cancel transcoding for a video
     */
    fun cancelTranscoding(videoId: String) {
        transcodingManager.cancelTranscoding(videoId)
        _pendingQueue.value = _pendingQueue.value - videoId
    }
    
    /**
     * Clear transcoded cache
     */
    fun clearTranscodedCache() {
        transcodingManager.clearTranscodedCache()
        _transcodedVideos.value = emptySet()
        _pendingQueue.value = emptySet()
        Log.d(TAG, "Cleared all transcoding data")
    }
    
    /**
     * Get cache size in bytes
     */
    fun getTranscodedCacheSize(): Long {
        return transcodingManager.getTranscodedCacheSize()
    }
    
    /**
     * Get cache size in human-readable format
     */
    fun getTranscodedCacheSizeFormatted(): String {
        val bytes = getTranscodedCacheSize()
        val mb = bytes / 1024 / 1024
        return if (mb > 0) "${mb}MB" else "${bytes / 1024}KB"
    }
}

