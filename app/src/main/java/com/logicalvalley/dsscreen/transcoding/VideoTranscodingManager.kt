package com.logicalvalley.dsscreen.transcoding

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.resize.AspectRatioResizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Future

/**
 * Manages on-device video transcoding for videos that exceed device capabilities
 * Uses Android's native MediaCodec API via Transcoder library
 */
class VideoTranscodingManager(private val context: Context) {
    
    private val TAG = "VideoTranscoding"
    
    // Track transcoding jobs
    private val _transcodingJobs = MutableStateFlow<Map<String, TranscodingJob>>(emptyMap())
    val transcodingJobs: StateFlow<Map<String, TranscodingJob>> = _transcodingJobs.asStateFlow()
    
    // Track active transcoding futures for cancellation
    private val activeFutures = mutableMapOf<String, Future<Void>>()
    
    // Cache directory for transcoded videos
    private val transcodedCacheDir: File by lazy {
        File(context.cacheDir, "transcoded_videos").apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created transcoded cache directory: $absolutePath")
            }
        }
    }
    
    /**
     * Get optimal resolution for this device
     */
    fun getDeviceOptimalResolution(): Pair<Int, Int> {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val deviceWidth = displayMetrics.widthPixels
        val deviceHeight = displayMetrics.heightPixels
        
        Log.d(TAG, "Device display: ${deviceWidth}x${deviceHeight}")
        
        // Determine optimal resolution based on device display
        return when {
            deviceWidth <= 1280 -> 1280 to 720  // 720p for low-res devices/emulators
            deviceWidth <= 1920 -> 1920 to 1080 // 1080p for mid-range
            else -> 1920 to 1080                 // Cap at 1080p for safety
        }
    }
    
    /**
     * Check if a transcoded version exists
     */
    fun hasTranscodedVersion(videoId: String): Boolean {
        val transcodedFile = File(transcodedCacheDir, "${videoId}_transcoded.mp4")
        val exists = transcodedFile.exists() && transcodedFile.length() > 0
        if (exists) {
            Log.d(TAG, "Transcoded version exists for $videoId: ${transcodedFile.length() / 1024 / 1024}MB")
        }
        return exists
    }
    
    /**
     * Get path to transcoded video
     */
    fun getTranscodedVideoPath(videoId: String): String? {
        val transcodedFile = File(transcodedCacheDir, "${videoId}_transcoded.mp4")
        return if (transcodedFile.exists()) {
            Log.d(TAG, "Returning transcoded path: ${transcodedFile.absolutePath}")
            transcodedFile.absolutePath
        } else null
    }
    
    /**
     * Start transcoding a video in the background
     */
    suspend fun transcodeVideo(
        videoId: String,
        sourceVideoPath: String,
        onProgress: (Float) -> Unit = {},
        onComplete: (success: Boolean, outputPath: String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "=".repeat(60))
        Log.d(TAG, "Starting transcoding for video: $videoId")
        Log.d(TAG, "Source: $sourceVideoPath")
        
        // Check if already transcoding
        if (_transcodingJobs.value.containsKey(videoId)) {
            Log.w(TAG, "Video $videoId is already being transcoded")
            return@withContext
        }
        
        // Check if source file exists
        val sourceFile = File(sourceVideoPath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: $sourceVideoPath")
            onComplete(false, null)
            return@withContext
        }
        
        // Create output file
        val outputFile = File(transcodedCacheDir, "${videoId}_transcoded.mp4")
        if (outputFile.exists()) {
            Log.d(TAG, "Transcoded version already exists, using it: ${outputFile.absolutePath}")
            onComplete(true, outputFile.absolutePath)
            return@withContext
        }
        
        // Get optimal resolution for device
        val (targetWidth, targetHeight) = getDeviceOptimalResolution()
        
        // Create transcoding job entry
        val job = TranscodingJob(
            videoId = videoId,
            sourceFile = sourceVideoPath,
            outputFile = outputFile.absolutePath,
            targetResolution = "${targetWidth}x${targetHeight}",
            status = TranscodingStatus.IN_PROGRESS,
            progress = 0f
        )
        
        _transcodingJobs.value = _transcodingJobs.value + (videoId to job)
        
        Log.d(TAG, "Transcoding to $targetWidth x $targetHeight")
        Log.d(TAG, "Output: ${outputFile.absolutePath}")
        
        try {
            // Verify source file
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                Log.e(TAG, "Source file invalid: exists=${sourceFile.exists()}, size=${sourceFile.length()}")
                throw IllegalStateException("Source file is invalid")
            }
            
            Log.d(TAG, "Source file verified: ${sourceFile.length() / 1024 / 1024}MB")
            
            // Get source video info
            val mediaMetadataRetriever = android.media.MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(sourceVideoPath)
            val width = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            mediaMetadataRetriever.release()
            
            Log.d(TAG, "Source video resolution: ${width}x${height}")
            
            // Check if source resolution is too high for device to decode
            if (width > 3000 || height > 2000) {
                Log.e(TAG, "⚠️ Source resolution (${width}x${height}) is too high for device to decode/transcode")
                Log.e(TAG, "This device can only transcode videos up to ~2560x1440")
                Log.e(TAG, "Please use a lower resolution source or test on a real device")
                throw IllegalStateException("Source resolution too high for device transcoding capabilities")
            }
            
            // Configure transcoding strategy
            val strategy = DefaultVideoStrategy.Builder()
                .addResizer(AspectRatioResizer(targetWidth.toFloat()))
                .bitRate(5_000_000L) // 5 Mbps
                .frameRate(30) // 30 fps
                .keyFrameInterval(3f) // Key frame every 3 seconds
                .build()
            
            Log.d(TAG, "Strategy configured, starting transcoder...")
            
            // Start transcoding
            val future = Transcoder.into(outputFile.absolutePath)
                .addDataSource(context, android.net.Uri.fromFile(sourceFile))
                .setVideoTrackStrategy(strategy)
                .setListener(object : TranscoderListener {
                    override fun onTranscodeProgress(progress: Double) {
                        val progressFloat = progress.toFloat()
                        Log.d(TAG, "📊 Transcoding progress for $videoId: ${(progressFloat * 100).toInt()}%")
                        
                        // Update job progress on main thread
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            _transcodingJobs.value = _transcodingJobs.value + (videoId to job.copy(progress = progressFloat))
                            onProgress(progressFloat)
                        }
                    }
                    
                    override fun onTranscodeCompleted(successCode: Int) {
                        Log.d(TAG, "=".repeat(60))
                        Log.d(TAG, "✅ Transcoding completed successfully for $videoId")
                        Log.d(TAG, "Success code: $successCode")
                        Log.d(TAG, "Output file size: ${outputFile.length() / 1024 / 1024}MB")
                        Log.d(TAG, "=".repeat(60))
                        
                        // Update job status on main thread
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            _transcodingJobs.value = _transcodingJobs.value + (videoId to job.copy(
                                status = TranscodingStatus.COMPLETED,
                                progress = 1f
                            ))
                            
                            // Remove from active futures
                            activeFutures.remove(videoId)
                            
                            onComplete(true, outputFile.absolutePath)
                        }
                    }
                    
                    override fun onTranscodeFailed(exception: Throwable) {
                        Log.e(TAG, "=".repeat(60))
                        Log.e(TAG, "❌ Transcoding failed for $videoId", exception)
                        Log.e(TAG, "Exception type: ${exception.javaClass.simpleName}")
                        Log.e(TAG, "Exception message: ${exception.message}")
                        exception.printStackTrace()
                        Log.e(TAG, "=".repeat(60))
                        
                        // Clean up failed output
                        if (outputFile.exists()) {
                            outputFile.delete()
                            Log.d(TAG, "Deleted failed output file")
                        }
                        
                        // Update job status on main thread
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            _transcodingJobs.value = _transcodingJobs.value + (videoId to job.copy(
                                status = TranscodingStatus.FAILED,
                                error = exception.message
                            ))
                            
                            // Remove from active futures
                            activeFutures.remove(videoId)
                            
                            onComplete(false, null)
                        }
                    }
                    
                    override fun onTranscodeCanceled() {
                        Log.w(TAG, "⚠️ Transcoding cancelled for $videoId")
                        
                        // Clean up cancelled output
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                        
                        // Update job status on main thread
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            _transcodingJobs.value = _transcodingJobs.value - videoId
                            
                            // Remove from active futures
                            activeFutures.remove(videoId)
                            
                            onComplete(false, null)
                        }
                    }
                })
                .transcode()
            
            // Store future for potential cancellation
            activeFutures[videoId] = future
            
            Log.d(TAG, "🚀 Transcoding started successfully in background for $videoId")
            Log.d(TAG, "Transcoder future: ${future.javaClass.simpleName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception starting transcoding for $videoId", e)
            
            // Clean up failed output
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            // Update job status
            _transcodingJobs.value = _transcodingJobs.value + (videoId to job.copy(
                status = TranscodingStatus.FAILED,
                error = e.message
            ))
            
            onComplete(false, null)
        }
    }
    
    /**
     * Cancel transcoding job
     */
    fun cancelTranscoding(videoId: String) {
        activeFutures[videoId]?.cancel(true)
        _transcodingJobs.value = _transcodingJobs.value - videoId
        Log.d(TAG, "Cancelled transcoding for $videoId")
    }
    
    /**
     * Clear all transcoded videos (to free up space)
     */
    fun clearTranscodedCache() {
        // Cancel all active transcodings
        activeFutures.values.forEach { it.cancel(true) }
        activeFutures.clear()
        
        // Delete transcoded files
        val deletedCount = transcodedCacheDir.listFiles()?.count { it.delete() } ?: 0
        _transcodingJobs.value = emptyMap()
        
        Log.d(TAG, "Cleared transcoded video cache ($deletedCount files deleted)")
    }
    
    /**
     * Get cache size in bytes
     */
    fun getTranscodedCacheSize(): Long {
        return transcodedCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}

/**
 * Transcoding job status
 */
data class TranscodingJob(
    val videoId: String,
    val sourceFile: String,
    val outputFile: String,
    val targetResolution: String,
    val status: TranscodingStatus,
    val progress: Float = 0f,
    val error: String? = null
)

enum class TranscodingStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

