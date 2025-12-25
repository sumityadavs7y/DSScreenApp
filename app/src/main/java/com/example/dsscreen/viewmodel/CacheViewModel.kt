package com.example.dsscreen.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.dsscreen.cache.VideoCacheManager
import com.example.dsscreen.cache.VideoDownloadManager
import com.example.dsscreen.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing video caching
 */
@UnstableApi
class CacheViewModel(
    private val context: Context
) : ViewModel() {
    
    private val TAG = "CacheViewModel"
    private val cache = VideoCacheManager.getCache(context)
    private val downloadManager = VideoDownloadManager(context, cache)
    
    private val _videoCacheStatus = MutableStateFlow<Map<String, VideoCacheInfo>>(emptyMap())
    val videoCacheStatus: StateFlow<Map<String, VideoCacheInfo>> = _videoCacheStatus.asStateFlow()
    
    private val _overallProgress = MutableStateFlow(0f)
    val overallProgress: StateFlow<Float> = _overallProgress.asStateFlow()

    /**
     * Get cached data source factory for ExoPlayer
     */
    fun getCachedDataSourceFactory() = downloadManager.createCachedDataSourceFactory()

    /**
     * Check cache status for all videos in playlist
     */
    fun checkCacheStatus(playlist: Playlist, baseUrl: String) {
        viewModelScope.launch {
            val items = playlist.items?.sortedBy { it.order } ?: run {
                Log.w(TAG, "No playlist items to check")
                return@launch
            }
            
            Log.d(TAG, "Checking cache status for ${items.size} videos...")
            
            val statusMap = mutableMapOf<String, VideoCacheInfo>()
            var totalCached = 0
            
            items.forEachIndexed { index, item ->
                val videoId = item.video?.id ?: run {
                    Log.w(TAG, "Video at index $index has no ID, skipping")
                    return@forEachIndexed
                }
                val videoUrl = "${baseUrl}api/videos/$videoId/download"
                val isCached = downloadManager.isVideoCached(videoUrl)
                
                statusMap[videoId] = VideoCacheInfo(
                    videoId = videoId,
                    videoName = item.video?.fileName ?: "Unknown",
                    isCached = isCached,
                    progress = if (isCached) 100 else 0
                )
                
                if (isCached) totalCached++
                
                Log.d(TAG, "  Video ${index + 1}/${items.size}: ${item.video?.fileName} - ${if (isCached) "✓ CACHED" else "○ Not cached"}")
            }
            
            _videoCacheStatus.value = statusMap
            val overallProg = if (items.isNotEmpty()) {
                totalCached.toFloat() / items.size.toFloat()
            } else {
                0f
            }
            _overallProgress.value = overallProg
            
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "Cache Summary: $totalCached/${items.size} videos cached (${(overallProg * 100).toInt()}%)")
            Log.d(TAG, "Cache map size: ${statusMap.size}")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    /**
     * Update cache status after video plays
     */
    fun updateCacheStatusAfterPlay(videoId: String, baseUrl: String) {
        viewModelScope.launch {
            val videoUrl = "${baseUrl}api/videos/$videoId/download"
            val isCached = downloadManager.isVideoCached(videoUrl)
            
            Log.d(TAG, "Checking cache for video $videoId at $videoUrl: cached=$isCached")
            
            val currentStatus = _videoCacheStatus.value.toMutableMap()
            val previouslyCached = currentStatus[videoId]?.isCached ?: false
            
            // Update existing entry or create new one
            val existingInfo = currentStatus[videoId]
            if (existingInfo != null) {
                currentStatus[videoId] = existingInfo.copy(
                    isCached = isCached,
                    progress = if (isCached) 100 else 0
                )
            } else {
                // Video not in map yet, add it
                currentStatus[videoId] = VideoCacheInfo(
                    videoId = videoId,
                    videoName = "Video",
                    isCached = isCached,
                    progress = if (isCached) 100 else 0
                )
                Log.d(TAG, "Added video $videoId to cache status map")
            }
            
            _videoCacheStatus.value = currentStatus
            
            // Recalculate overall progress
            val totalCached = currentStatus.values.count { it.isCached }
            val totalVideos = currentStatus.size
            val newProgress = if (totalVideos > 0) {
                totalCached.toFloat() / totalVideos.toFloat()
            } else {
                0f
            }
            
            _overallProgress.value = newProgress
            
            if (!previouslyCached && isCached) {
                Log.d(TAG, "✓ Video $videoId newly cached! Progress: ${(newProgress * 100).toInt()}% ($totalCached/$totalVideos)")
            } else if (isCached && previouslyCached) {
                Log.d(TAG, "Video $videoId still cached. Progress: ${(newProgress * 100).toInt()}% ($totalCached/$totalVideos)")
            } else if (!isCached) {
                Log.w(TAG, "⚠ Video $videoId not yet cached. May need more playback time. Progress: ${(newProgress * 100).toInt()}% ($totalCached/$totalVideos)")
            }
        }
    }

    /**
     * Clear all cached videos
     */
    fun clearCache() {
        VideoCacheManager.clearCache()
        _videoCacheStatus.value = emptyMap()
        _overallProgress.value = 0f
        Log.d(TAG, "Cache cleared")
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release cache here as it's used globally
    }
}

/**
 * Video cache information
 */
data class VideoCacheInfo(
    val videoId: String,
    val videoName: String,
    val isCached: Boolean,
    val progress: Int
)

/**
 * Caching status sealed class
 */
sealed class CachingStatus {
    object Idle : CachingStatus()
    data class Downloading(val completed: Int, val total: Int) : CachingStatus()
    object Completed : CachingStatus()
    data class Error(val message: String) : CachingStatus()
}

