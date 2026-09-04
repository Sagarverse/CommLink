package com.commvault.commlink.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.commvault.commlink.MainActivity
import com.commvault.commlink.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("ALARM_TITLE") ?: "Scheduled Alarm"
        val message = intent.getStringExtra("ALARM_MESSAGE") ?: "It's time!"
        val alarmType = intent.getStringExtra("ALARM_TYPE") ?: "Standard"
        val maxVolume = intent.getIntExtra("MAX_VOLUME", 10)
        val intervalSecs = intent.getIntExtra("INTERVAL_SECS", 5)
        val repeatCount = intent.getIntExtra("REPEAT_COUNT", 10)
        
        // Start playing the alarm sound based on type
        when (alarmType) {
            "Standard" -> AlarmSoundPlayer.playStandard(context)
            "Escalating" -> AlarmSoundPlayer.playEscalating(context, maxVolume)
            "Interval" -> AlarmSoundPlayer.playInterval(context, intervalSecs, repeatCount)
            else -> AlarmSoundPlayer.playStandard(context)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "scheduled_alarms_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Scheduled Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alarms with stop actions"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Main intent to open the app
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop intent
        val stopIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.commvault_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(mainPendingIntent)
            .setFullScreenIntent(mainPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ALARM", stopPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
