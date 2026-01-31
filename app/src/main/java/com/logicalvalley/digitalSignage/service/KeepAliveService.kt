package com.logicalvalley.digitalSignage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.logicalvalley.digitalSignage.MainActivity
import com.logicalvalley.digitalSignage.R

/**
 * Foreground service to keep the app running in the background.
 * This prevents Android from killing the app to save battery.
 */
class KeepAliveService : Service() {
    private val TAG = "KeepAliveService"
    
    companion object {
        private const val CHANNEL_ID = "DigitalSignageChannel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔧 KeepAliveService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ KeepAliveService started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // START_STICKY ensures the service restarts if killed by the system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 KeepAliveService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Digital Signage Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Digital Signage running continuously"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Digital Signage")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

