package com.commvault.commlink.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.commvault.commlink.MainActivity
import com.commvault.commlink.R
import java.util.Calendar

class HydrationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("HYDRATION_LABEL") ?: "Rehydrate Yourself"
        val reminderId = intent.getStringExtra("HYDRATION_REMINDER_ID") ?: ""
        val hour = intent.getIntExtra("HYDRATION_HOUR", -1)
        val minute = intent.getIntExtra("HYDRATION_MINUTE", -1)

        Log.d("HydrationReminder", "Receiver fired! label=$label, hour=$hour, minute=$minute")

        // Show notification
        showNotification(context, label)

        // Re-schedule for tomorrow (self-rescheduling for daily repeat)
        if (hour >= 0 && minute >= 0 && reminderId.isNotBlank()) {
            rescheduleForTomorrow(context, reminderId, hour, minute, label)
        }
    }

    private fun showNotification(context: Context, label: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hydration_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hydration Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily hydration reminders"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.commvault_logo)
            .setContentTitle("💧 Hydration Reminder")
            .setContentText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$label\n\nStay hydrated for better focus and productivity! 💪"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Use a unique notification ID based on current time
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + 2000
        notificationManager.notify(notifId, notification)
    }

    private fun rescheduleForTomorrow(context: Context, reminderId: String, hour: Int, minute: Int, label: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HydrationReminderReceiver::class.java).apply {
            putExtra("HYDRATION_LABEL", label)
            putExtra("HYDRATION_REMINDER_ID", reminderId)
            putExtra("HYDRATION_HOUR", hour)
            putExtra("HYDRATION_MINUTE", minute)
        }
        val requestCode = reminderId.hashCode() + 5000
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule for tomorrow at the same time
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            Log.d("HydrationReminder", "Rescheduled for tomorrow at $hour:$minute")
        } catch (e: Exception) {
            Log.e("HydrationReminder", "Failed to reschedule", e)
        }
    }
}
