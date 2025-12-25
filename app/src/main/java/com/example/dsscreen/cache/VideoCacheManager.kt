package com.example.dsscreen.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Manages video caching for offline playback
 */
object VideoCacheManager {
    private const val CACHE_DIR_NAME = "video_cache"
    private const val MAX_CACHE_SIZE = 1024L * 1024 * 1024 // 1 GB for offline support

    @Volatile
    private var cache: SimpleCache? = null

    /**
     * Get or create the video cache
     */
    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            
            // Ensure cache directory exists
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
                android.util.Log.d("VideoCacheManager", "Created cache directory: ${cacheDir.absolutePath}")
            }
            
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
            android.util.Log.d("VideoCacheManager", "Cache initialized with ${MAX_CACHE_SIZE / 1024 / 1024}MB limit")
        }
        return cache!!
    }

    /**
     * Clear all cached videos
     */
    fun clearCache() {
        cache?.let {
            try {
                it.keys.forEach { key ->
                    it.removeResource(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get cache size in bytes
     */
    fun getCacheSize(): Long {
        return cache?.cacheSpace ?: 0L
    }
    
    /**
     * Check if a video is cached
     */
    fun isVideoCached(context: Context, videoUrl: String): Boolean {
        val cache = getCache(context)
        val cachedBytes = cache.getCachedBytes(videoUrl, 0, -1)
        val isCached = cachedBytes > 0
        
        android.util.Log.d("VideoCacheManager", "Cache check for $videoUrl: ${cachedBytes / 1024 / 1024}MB cached, isCached=$isCached")
        
        return isCached
    }
    
    /**
     * Get cache progress for a video (0-100)
     */
    fun getCacheProgress(context: Context, videoUrl: String): Int {
        try {
            val cache = getCache(context)
            val cachedBytes = cache.getCachedBytes(videoUrl, 0, -1)
            
            if (cachedBytes > 0) {
                android.util.Log.d("VideoCacheManager", "Video has ${cachedBytes / 1024 / 1024}MB cached")
                // If we have any cached data, consider it fully cached
                // (ExoPlayer's cache doesn't track total size easily)
                return 100
            }
            
            return 0
        } catch (e: Exception) {
            android.util.Log.e("VideoCacheManager", "Error getting cache progress", e)
            return 0
        }
    }

    /**
     * Release cache resources
     */
    fun release() {
        cache?.release()
        cache = null
    }
}

