package com.commvault.commlink.data.audiobridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.commvault.commlink.MainActivity

class UsbBridgeService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "STOP_ALL" -> {
                // Handle stop all
                stopSelf()
            }
            "EMERGENCY_CUTOFF" -> {
                // Emergency tear down
                stopSelf()
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("USB Audio Bridge Active", "Laptop ↔ Phone AI"))
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Bridge",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains the USB/Bluetooth Audio Bridge connection"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, content: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, UsbBridgeService::class.java).apply { action = "STOP_ALL" }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val emergencyIntent = Intent(this, UsbBridgeService::class.java).apply { action = "EMERGENCY_CUTOFF" }
        val emergencyPendingIntent = PendingIntent.getService(this, 2, emergencyIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play) // Placeholder icon
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop All", stopPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Emergency Cut-off", emergencyPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "audio_bridge_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
