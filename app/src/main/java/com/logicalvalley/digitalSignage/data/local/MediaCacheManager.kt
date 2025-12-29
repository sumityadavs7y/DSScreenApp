package com.logicalvalley.digitalSignage.data.local

import android.content.Context
import com.logicalvalley.digitalSignage.data.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MediaCacheManager(private val context: Context) {
    private val cacheDir = File(context.filesDir, "media_cache")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getLocalFile(item: PlaylistItem): File? {
        val fileName = item.video?.fileName ?: return null
        val file = File(cacheDir, fileName)
        return if (file.exists()) file else null
    }

    suspend fun downloadMedia(item: PlaylistItem, baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val fileName = item.video?.fileName ?: return@withContext false
        val videoId = item.video.id
        val url = URL("$baseUrl/api/media/$videoId/download")
        val file = File(cacheDir, fileName)

        if (file.exists() && file.length() > 0) {
            return@withContext true
        }

        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false
            }

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (file.exists()) file.delete()
            false
        }
    }

    fun getCacheProgress(items: List<PlaylistItem>): Float {
        if (items.isEmpty()) return 1f
        val cachedCount = items.count { getLocalFile(it) != null }
        return cachedCount.toFloat() / items.size
    }
}

