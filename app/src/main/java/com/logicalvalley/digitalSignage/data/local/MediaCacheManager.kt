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
import java.io.RandomAccessFile

class MediaCacheManager(private val context: Context) {
    private val TAG = "MediaCacheManager"
    private val cacheDir = File(context.filesDir, "media_cache")
    private val okHttpClient = SSLConfig.createOkHttpClient()
    
    companion object {
        private const val MIN_FREE_STORAGE_BYTES = 500L * 1024 * 1024
        private const val BUFFER_SIZE = 65536 // 64KB buffer for faster I/O
    }
    
    data class DownloadProgress(
        val itemId: String,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percentage: Float
    ) {
        fun getProgressText(): String {
            val downloadedMB = downloadedBytes / 1024 / 1024
            val totalMB = totalBytes / 1024 / 1024
            return "${String.format("%.2f", percentage)}% ($downloadedMB / $totalMB MB)"
        }
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(TAG, "📁 Created cache directory: ${cacheDir.absolutePath}")
        }
        cleanupInvalidFiles()
    }
    
    private fun cleanupInvalidFiles() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() < 1024) {
                    file.delete()
                    Log.d(TAG, "🗑️ Removed invalid file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up files", e)
        }
    }
    
    private fun ensureExtension(fileName: String): String {
        val validExtensions = listOf(".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v", ".jpg", ".jpeg", ".png", ".gif")
        return if (validExtensions.any { fileName.lowercase().endsWith(it) }) {
            fileName
        } else {
            "$fileName.mp4"
        }
    }

    fun getLocalFile(item: PlaylistItem): File? {
        val fileName = ensureExtension(item.video?.fileName ?: return null)
        val file = File(cacheDir, fileName)
        val expectedSize = item.video?.fileSize ?: 0L
        
        // Strict check: file exists and has reasonable size (> 1KB)
        if (file.exists() && file.canRead()) {
             // If we have a valid expected size, enforce it
             if (expectedSize > 0) {
                 if (file.length() == expectedSize) {
                     Log.d(TAG, "✅ Found cached file: $fileName (${file.length() / 1024}KB)")
                     return file
                 } else {
                     // File exists but size doesn't match (partial or corrupted)
                     // Do NOT return it, so it's treated as not cached
                     return null
                 }
             }
             
             // Fallback for when expected size is 0 (shouldn't happen with valid API data)
             if (file.length() > 1024) {
                 Log.d(TAG, "✅ Found cached file (no size check): $fileName")
                 return file
             }
        }
        
        return null
    }

    suspend fun downloadMedia(
        videoId: String,
        fileName: String,
        fileSize: Long,
        itemId: String, // For progress reporting
        baseUrl: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val actualFileName = ensureExtension(fileName)
        val downloadUrl = "$baseUrl/api/media/$videoId/download"
        val file = File(cacheDir, actualFileName)
        val expectedSize = fileSize

        try {
            // Check if already complete
            if (file.exists() && file.canRead()) {
                val currentLength = file.length()
                if (expectedSize > 0 && currentLength == expectedSize) {
                    Log.d(TAG, "✅ Already cached (verified size): $actualFileName")
                    return@withContext true
                } else if (expectedSize > 0 && currentLength > expectedSize) {
                    Log.w(TAG, "⚠️ File larger than expected ($currentLength > $expectedSize). Deleting and re-downloading.")
                    file.delete()
                } else if (currentLength > 1024 && expectedSize <= 0) {
                    // Fallback for when expected size is unknown but file seems valid
                    Log.d(TAG, "✅ Already cached (unknown size, >1KB): $actualFileName")
                    return@withContext true
                }
            }

            // Check storage
            val stat = StatFs(cacheDir.path)
            val available = stat.availableBytes
            if (available < expectedSize + MIN_FREE_STORAGE_BYTES) {
                Log.e(TAG, "❌ Insufficient storage for $actualFileName")
                return@withContext false
            }

            // Get existing file size for resume
            val existingSize = if (file.exists()) file.length() else 0L
            
            // Build request with Range header for resume
            val requestBuilder = Request.Builder().url(downloadUrl)
            if (existingSize > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingSize-")
                Log.d(TAG, "📥 Resuming download: $actualFileName from ${existingSize / 1024}KB")
            } else {
                Log.d(TAG, "📥 Starting download: $actualFileName")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                Log.e(TAG, "❌ Download failed: HTTP ${response.code}")
                return@withContext false
            }

            // Get total size (handle both 200 and 206 responses)
            val contentLength = response.body?.contentLength() ?: -1L
            val totalBytes = if (response.code == 206) {
                existingSize + contentLength
            } else {
                contentLength
            }

            // Open file for writing (append if resuming, overwrite if new)
            val randomAccessFile = RandomAccessFile(file, "rw")
            if (response.code == 206) {
                randomAccessFile.seek(existingSize)
            } else {
                // New download, truncate file to 0
                randomAccessFile.setLength(0)
            }

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = existingSize
            var lastProgress = -1f

            response.body?.byteStream()?.use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    randomAccessFile.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (totalBytes > 0) {
                        val progress = (totalBytesRead.toFloat() / totalBytes.toFloat()) * 100f
                        if (kotlin.math.abs(progress - lastProgress) >= 0.1f) {
                            lastProgress = progress
                            onProgress?.invoke(
                                DownloadProgress(
                                    itemId = itemId,
                                    fileName = actualFileName,
                                    downloadedBytes = totalBytesRead,
                                    totalBytes = totalBytes,
                                    percentage = progress
                                )
                            )
                        }
                    }
                }
            }
            
            // Ensure 100% is reported at the end if download is complete
            if (totalBytes > 0 && totalBytesRead >= totalBytes) {
                 onProgress?.invoke(
                    DownloadProgress(
                        itemId = itemId,
                        fileName = actualFileName,
                        downloadedBytes = totalBytesRead,
                        totalBytes = totalBytes,
                        percentage = 100f
                    )
                )
            }

            randomAccessFile.close()

            // Ensure file has read permissions
            try {
                file.setReadable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not set read permissions: ${e.message}")
            }

            // Validation: check file exists, has reasonable size, and is readable
            val finalSize = file.length()
            val canRead = file.canRead()
            
            val isSizeCorrect = if (fileSize > 0) finalSize == fileSize else finalSize > 1024

            if (isSizeCorrect && canRead) {
                Log.d(TAG, "✅ Downloaded: $actualFileName (${finalSize / 1024}KB, readable: $canRead)")
                Log.d(TAG, "📂 Saved to: ${file.absolutePath}")
                return@withContext true
            } else {
                Log.e(TAG, "❌ Download incomplete: $actualFileName - Size: ${finalSize} bytes (Expected: $fileSize), Readable: $canRead")
                if (file.exists()) {
                    file.delete()
                }
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Download error: $actualFileName - ${e.message}")
            return@withContext false
        }
    }

    fun getCacheProgress(items: List<PlaylistItem>): Float {
        if (items.isEmpty()) return 1f
        val cached = items.count { getLocalFile(it) != null }
        return cached.toFloat() / items.size
    }

    fun clearOrphanedCache(currentItems: List<PlaylistItem>) {
        try {
            val validNames = currentItems.mapNotNull { 
                it.video?.fileName?.let { name -> ensureExtension(name) }
            }.toSet()
            
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in validNames) {
                    file.delete()
                    Log.d(TAG, "🗑️ Removed orphaned: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    data class StorageStats(
        val totalBytes: Long,
        val availableBytes: Long,
        val cacheBytes: Long
    )

    fun getStorageStats(): StorageStats {
        val stat = StatFs(cacheDir.path)
        val cacheSize = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        return StorageStats(
            totalBytes = stat.totalBytes,
            availableBytes = stat.availableBytes,
            cacheBytes = cacheSize
        )
    }
}
