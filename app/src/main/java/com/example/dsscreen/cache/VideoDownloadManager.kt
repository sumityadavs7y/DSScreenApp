package com.example.dsscreen.cache

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages downloading and caching of videos
 */
@UnstableApi
class VideoDownloadManager(
    private val context: Context,
    private val cache: SimpleCache
) {
    private val TAG = "VideoDownloadManager"
    
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    /**
     * Check if a video is cached
     */
    fun isVideoCached(videoUrl: String): Boolean {
        val cachedBytes = cache.getCachedBytes(videoUrl, 0, -1)
        val isCached = cachedBytes > 0
        
        Log.d(TAG, "Video cached check for $videoUrl: ${cachedBytes / 1024 / 1024}MB cached, isCached=$isCached")
        
        return isCached
    }
    
    /**
     * Get cache progress for a video (0-100)
     */
    fun getCacheProgress(videoUrl: String): Int {
        try {
            val cachedBytes = cache.getCachedBytes(videoUrl, 0, -1)
            
            if (cachedBytes > 0) {
                Log.d(TAG, "Video has ${cachedBytes / 1024 / 1024}MB cached")
                // If we have any cached data, consider it fully cached
                return 100
            }
            
            return 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache progress", e)
            return 0
        }
    }

    /**
     * Create a cached data source factory for ExoPlayer
     * This will automatically cache videos as they stream
     */
    fun createCachedDataSourceFactory(): DataSource.Factory {
        Log.d(TAG, "Creating cached data source factory")
        
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
        
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            // Important: Don't set cache write sink factory to enable writing
            // Setting to null allows default cache writing behavior
            // Continue on error, prefer cache when available
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .also {
                Log.d(TAG, "Cache data source factory created. Cache size: ${cache.cacheSpace / 1024 / 1024}MB")
            }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        val keys = cache.keys
        val cacheSize = cache.cacheSpace
        return "Cached videos: ${keys.size}, Size: ${cacheSize / 1024 / 1024}MB"
    }
}

