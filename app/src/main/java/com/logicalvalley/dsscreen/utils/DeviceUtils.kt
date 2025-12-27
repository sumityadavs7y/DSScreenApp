package com.logicalvalley.dsscreen.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.logicalvalley.dsscreen.data.model.DeviceInfo
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class for device-related operations
 */
object DeviceUtils {
    /**
     * Generate a unique device UID based on Android ID
     * Format: AND-{12-char-hash}
     */
    fun generateDeviceUID(context: Context): String {
        // Get Android ID (survives factory reset on Android 8+)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // Combine with device info for better uniqueness
        val uniqueString = buildString {
            append(androidId)
            append(Build.MANUFACTURER)
            append(Build.MODEL)
            append(Build.BRAND)
        }

        // Hash the combined string
        val hash = hashString(uniqueString)
        return "AND-$hash"
    }

    /**
     * Get device information
     */
    fun getDeviceInfo(context: Context): DeviceInfo {
        val displayMetrics = context.resources.displayMetrics
        val resolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        
        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US
        ).format(Date())

        return DeviceInfo(
            resolution = resolution,
            deviceModel = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            timestamp = timestamp,
            location = "Android TV" // Can be customized
        )
    }

    /**
     * Hash a string using SHA-256 and return first 12 characters
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
            .take(12)
            .uppercase(Locale.US)
    }
}

