package com.logicalvalley.digitalSignage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.logicalvalley.digitalSignage.MainActivity

/**
 * BroadcastReceiver that starts the app when the device boots up.
 * Essential for digital signage devices that need to start automatically.
 */
class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "🚀 Device boot detected! Starting Digital Signage app...")
            
            // Start the main activity
            val startIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            
            context.startActivity(startIntent)
            Log.d(TAG, "✅ App launch initiated")
        }
    }
}

