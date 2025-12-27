package com.logicalvalley.dsscreen.cache

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

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
    
    private var downloadJob: Job? = null
    private val activeDownloads = mutableSetOf<String>()

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
    
    /**
     * Export cached video to a temporary file for transcoding
     * Returns temp file path if successful, null otherwise
     */
    suspend fun exportCachedVideoToFile(videoUrl: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (!isVideoCached(videoUrl)) {
                Log.w(TAG, "Video not cached, cannot export: $videoUrl")
                return@withContext null
            }
            
            // Create temp file
            val tempFile = java.io.File.createTempFile("video_export_", ".mp4", context.cacheDir)
            
            Log.d(TAG, "Exporting cached video to: ${tempFile.absolutePath}")
            
            // Create a data source to read from cache
            val dataSource = createCachedDataSourceFactory().createDataSource()
            
            // Open the data source
            val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse(videoUrl))
            dataSource.open(dataSpec)
            
            // Read and write to temp file
            val outputStream = java.io.FileOutputStream(tempFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = 0L
            
            while (true) {
                bytesRead = dataSource.read(buffer, 0, buffer.size)
                if (bytesRead == androidx.media3.common.C.RESULT_END_OF_INPUT) {
                    break
                }
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            
            outputStream.close()
            dataSource.close()
            
            Log.d(TAG, "✅ Exported ${totalBytes / 1024 / 1024}MB to ${tempFile.absolutePath}")
            
            return@withContext tempFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error exporting cached video", e)
            return@withContext null
        }
    }
    
    /**
     * Pre-download a video to cache in background
     */
    suspend fun downloadVideoToCache(videoUrl: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (isVideoCached(videoUrl)) {
            Log.d(TAG, "Video already cached: $videoId")
            updateDownloadProgress(videoId, 100)
            return@withContext true
        }
        
        if (activeDownloads.contains(videoId)) {
            Log.d(TAG, "Video already downloading: $videoId")
            return@withContext false
        }
        
        activeDownloads.add(videoId)
        Log.d(TAG, "Starting background download for: $videoId")
        
        try {
            val dataSource = createCachedDataSourceFactory().createDataSource()
            val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse(videoUrl))
            
            dataSource.open(dataSpec)
            
            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var bytesRead: Int
            var totalBytes = 0L
            var lastProgressUpdate = 0
            
            while (isActive) {
                bytesRead = dataSource.read(buffer, 0, buffer.size)
                if (bytesRead == androidx.media3.common.C.RESULT_END_OF_INPUT) {
                    break
                }
                totalBytes += bytesRead
                
                // Update progress every 5%
                val progress = ((totalBytes / (10 * 1024 * 1024f)) * 100).toInt().coerceIn(0, 99) // Estimate
                if (progress >= lastProgressUpdate + 5) {
                    lastProgressUpdate = progress
                    updateDownloadProgress(videoId, progress)
                    Log.d(TAG, "Download progress for $videoId: $progress%")
                }
            }
            
            dataSource.close()
            
            updateDownloadProgress(videoId, 100)
            activeDownloads.remove(videoId)
            
            Log.d(TAG, "✅ Successfully downloaded ${totalBytes / 1024 / 1024}MB for video: $videoId")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading video: $videoId", e)
            activeDownloads.remove(videoId)
            updateDownloadProgress(videoId, 0)
            return@withContext false
        }
    }
    
    /**
     * Download multiple videos in sequence
     */
    suspend fun downloadVideosInBackground(videos: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting background download for ${videos.size} videos")
        
        videos.forEach { (videoId, videoUrl) ->
            if (!isActive) {
                Log.d(TAG, "Download cancelled")
                return@withContext
            }
            
            if (!isVideoCached(videoUrl)) {
                downloadVideoToCache(videoUrl, videoId)
            } else {
                updateDownloadProgress(videoId, 100)
            }
        }
        
        Log.d(TAG, "✅ All videos downloaded!")
    }
    
    private fun updateDownloadProgress(videoId: String, progress: Int) {
        val current = _downloadProgress.value.toMutableMap()
        current[videoId] = progress
        _downloadProgress.value = current
    }
    
    /**
     * Cancel all active downloads
     */
    fun cancelDownloads() {
        downloadJob?.cancel()
        activeDownloads.clear()
        Log.d(TAG, "All downloads cancelled")
    }
}

