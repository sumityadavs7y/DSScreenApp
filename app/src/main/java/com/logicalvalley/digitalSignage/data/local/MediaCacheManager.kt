package com.logicalvalley.digitalSignage.data.local

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.logicalvalley.digitalSignage.data.model.PlaylistItem
import com.logicalvalley.digitalSignage.util.SSLConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MediaCacheManager(private val context: Context) {
    private val TAG = "MediaCacheManager"
    private val cacheDir = File(context.filesDir, "media_cache")
    private val okHttpClient = SSLConfig.createOkHttpClient()
    
    companion object {
        // Minimum free storage to maintain on device (500 MB)
        private const val MIN_FREE_STORAGE_BYTES = 500L * 1024 * 1024
        
        // Safety buffer when checking if download will fit (100 MB)
        private const val STORAGE_SAFETY_BUFFER = 100L * 1024 * 1024
    }
    
    /**
     * Download progress information for a single item
     */
    data class DownloadProgress(
        val itemId: String,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percentage: Int
    ) {
        fun getProgressText(): String {
            val downloadedMB = downloadedBytes / 1024 / 1024
            val totalMB = totalBytes / 1024 / 1024
            return "$percentage% ($downloadedMB / $totalMB MB)"
        }
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(TAG, "📁 Cache directory created at: ${cacheDir.absolutePath}")
        } else {
            Log.d(TAG, "📁 Cache directory exists at: ${cacheDir.absolutePath}")
        }
    }

    fun getLocalFile(item: PlaylistItem): File? {
        val fileName = item.video?.fileName ?: return null
        val file = File(cacheDir, fileName)
        return if (file.exists() && file.length() > 0) {
            Log.d(TAG, "✅ Local file found: $fileName (${file.length()} bytes)")
            file
        } else {
            Log.d(TAG, "❌ Local file not found or empty: $fileName")
            null
        }
    }

    suspend fun downloadMedia(
        item: PlaylistItem, 
        baseUrl: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val fileName = item.video?.fileName ?: run {
            Log.e(TAG, "❌ No filename for item: ${item.id}")
            return@withContext false
        }
        
        val videoId = item.video.id
        val downloadUrl = "$baseUrl/api/media/$videoId/download"
        val file = File(cacheDir, fileName)

        // Check if already cached
        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "⏭️ Already cached: $fileName (${file.length()} bytes)")
            return@withContext true
        }

        // Check storage availability before downloading
        val estimatedSize = item.video.fileSize ?: 0L
        val storageCheck = checkStorageAvailable(estimatedSize)
        
        if (!storageCheck.hasSpace) {
            Log.e(TAG, "❌ Insufficient storage for $fileName")
            Log.e(TAG, "   Need: ${estimatedSize / 1024 / 1024} MB")
            Log.e(TAG, "   Available: ${storageCheck.availableBytes / 1024 / 1024} MB")
            Log.e(TAG, "   After download: ${(storageCheck.availableBytes - estimatedSize) / 1024 / 1024} MB")
            Log.e(TAG, "   Minimum required: ${MIN_FREE_STORAGE_BYTES / 1024 / 1024} MB")
            return@withContext false
        }

        Log.d(TAG, "⬇️ Downloading: $fileName from $downloadUrl")
        Log.d(TAG, "   Estimated size: ${estimatedSize / 1024 / 1024} MB")
        Log.d(TAG, "   Available storage: ${storageCheck.availableBytes / 1024 / 1024} MB")

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Download failed for $fileName: HTTP ${response.code} - ${response.message}")
                return@withContext false
            }

            val totalBytes = response.body?.contentLength() ?: -1L
            Log.d(TAG, "📦 Downloading $fileName: ${totalBytes / 1024} KB")

            response.body?.byteStream()?.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastReportedProgress = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Report progress
                        if (totalBytes > 0) {
                            val currentProgress = (totalBytesRead * 100 / totalBytes).toInt()
                            
                            // Report every 5% change or every 1 MB downloaded
                            if (currentProgress != lastReportedProgress || totalBytesRead % (1024 * 1024) == 0L) {
                                lastReportedProgress = currentProgress
                                
                                val progressInfo = DownloadProgress(
                                    itemId = item.id,
                                    fileName = fileName,
                                    downloadedBytes = totalBytesRead,
                                    totalBytes = totalBytes,
                                    percentage = currentProgress
                                )
                                
                                onProgress?.invoke(progressInfo)
                                
                                // Log progress for debugging
                                if (totalBytesRead % (1024 * 1024) == 0L) {
                                    Log.d(TAG, "📊 Progress $fileName: ${progressInfo.getProgressText()}")
                                }
                            }
                        }
                    }
                }
            }

            val finalSize = file.length()
            if (finalSize > 0) {
                Log.d(TAG, "✅ Successfully downloaded: $fileName (${finalSize / 1024} KB)")
                return@withContext true
            } else {
                Log.e(TAG, "❌ Downloaded file is empty: $fileName")
                file.delete()
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception downloading $fileName: ${e.javaClass.simpleName} - ${e.message}", e)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "🗑️ Deleted partial file: $fileName")
            }
            return@withContext false
        }
    }

    fun getCacheProgress(items: List<PlaylistItem>): Float {
        if (items.isEmpty()) return 1f
        val cachedCount = items.count { getLocalFile(it) != null }
        val progress = cachedCount.toFloat() / items.size
        Log.d(TAG, "📊 Cache progress: $cachedCount/${items.size} (${(progress * 100).toInt()}%)")
        return progress
    }
    
    /**
     * Get available storage on device
     */
    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(cacheDir.path)
            stat.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting available storage", e)
            0L
        }
    }
    
    /**
     * Get total device storage
     */
    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(cacheDir.path)
            stat.totalBytes
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting total storage", e)
            0L
        }
    }
    
    /**
     * Get current cache directory size
     */
    fun getCacheSizeBytes(): Long {
        return try {
            cacheDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating cache size", e)
            0L
        }
    }
    
    /**
     * Check if there's enough storage for a download
     */
    data class StorageCheckResult(
        val hasSpace: Boolean,
        val availableBytes: Long,
        val requiredBytes: Long,
        val reason: String?
    )
    
    fun checkStorageAvailable(requiredBytes: Long): StorageCheckResult {
        val available = getAvailableStorageBytes()
        
        // If file size unknown, check if we have at least minimum free storage
        if (requiredBytes <= 0) {
            val hasSpace = available > MIN_FREE_STORAGE_BYTES
            return StorageCheckResult(
                hasSpace = hasSpace,
                availableBytes = available,
                requiredBytes = MIN_FREE_STORAGE_BYTES,
                reason = if (!hasSpace) "Unknown file size, insufficient minimum storage" else null
            )
        }
        
        // Check if download would leave enough free space
        val spaceAfterDownload = available - requiredBytes
        val hasSpace = spaceAfterDownload >= (MIN_FREE_STORAGE_BYTES + STORAGE_SAFETY_BUFFER)
        
        return StorageCheckResult(
            hasSpace = hasSpace,
            availableBytes = available,
            requiredBytes = requiredBytes,
            reason = if (!hasSpace) {
                "Would leave only ${spaceAfterDownload / 1024 / 1024}MB free (need ${(MIN_FREE_STORAGE_BYTES + STORAGE_SAFETY_BUFFER) / 1024 / 1024}MB)"
            } else null
        )
    }
    
    /**
     * Clear orphaned cache files that don't belong to current playlist
     */
    fun clearOrphanedCache(currentPlaylistItems: List<PlaylistItem>) {
        try {
            val currentFileNames = currentPlaylistItems.mapNotNull { it.video?.fileName }.toSet()
            val cachedFiles = cacheDir.listFiles() ?: return
            
            var deletedCount = 0
            var freedBytes = 0L
            
            cachedFiles.forEach { file ->
                if (file.isFile && file.name !in currentFileNames) {
                    val fileSize = file.length()
                    if (file.delete()) {
                        deletedCount++
                        freedBytes += fileSize
                        Log.d(TAG, "🗑️ Deleted orphaned file: ${file.name} (${fileSize / 1024} KB)")
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "🧹 Cleanup complete: $deletedCount files deleted, ${freedBytes / 1024 / 1024} MB freed")
            } else {
                Log.d(TAG, "✅ No orphaned files found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing orphaned cache", e)
        }
    }
    
    /**
     * Get storage statistics
     */
    data class StorageStats(
        val totalBytes: Long,
        val availableBytes: Long,
        val usedBytes: Long,
        val cacheBytes: Long,
        val usedPercentage: Int
    )
    
    fun getStorageStats(): StorageStats {
        val total = getTotalStorageBytes()
        val available = getAvailableStorageBytes()
        val used = total - available
        val cache = getCacheSizeBytes()
        val usedPercent = if (total > 0) ((used * 100) / total).toInt() else 0
        
        return StorageStats(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            cacheBytes = cache,
            usedPercentage = usedPercent
        )
    }
}

