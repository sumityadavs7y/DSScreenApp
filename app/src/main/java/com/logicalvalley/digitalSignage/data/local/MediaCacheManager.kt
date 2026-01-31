package com.logicalvalley.digitalSignage.data.local

import android.content.Context
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

    suspend fun downloadMedia(item: PlaylistItem, baseUrl: String): Boolean = withContext(Dispatchers.IO) {
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

        Log.d(TAG, "⬇️ Downloading: $fileName from $downloadUrl")

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

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Log progress for large files
                        if (totalBytes > 0 && totalBytesRead % (1024 * 1024) == 0L) {
                            val progress = (totalBytesRead * 100 / totalBytes).toInt()
                            Log.d(TAG, "📊 Progress $fileName: $progress% (${totalBytesRead / 1024} KB / ${totalBytes / 1024} KB)")
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
}

