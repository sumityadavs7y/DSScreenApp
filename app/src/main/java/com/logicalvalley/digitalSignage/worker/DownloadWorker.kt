package com.logicalvalley.digitalSignage.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.logicalvalley.digitalSignage.data.local.MediaCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString("videoId") ?: return@withContext Result.failure()
        val fileName = inputData.getString("fileName") ?: return@withContext Result.failure()
        val fileSize = inputData.getLong("fileSize", 0L)
        val itemId = inputData.getString("itemId") ?: return@withContext Result.failure()
        val baseUrl = inputData.getString("baseUrl") ?: return@withContext Result.failure()

        val cacheManager = MediaCacheManager(applicationContext)

        val success = cacheManager.downloadMedia(
            videoId = videoId,
            fileName = fileName,
            fileSize = fileSize,
            itemId = itemId,
            baseUrl = baseUrl
        ) { progress ->
            setProgressAsync(
                workDataOf(
                    "progress" to progress.percentage,
                    "downloadedBytes" to progress.downloadedBytes,
                    "totalBytes" to progress.totalBytes
                )
            )
        }

        if (success) Result.success() else Result.retry()
    }
}
