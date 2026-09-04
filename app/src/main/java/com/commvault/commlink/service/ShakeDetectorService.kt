package com.commvault.commlink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.commvault.commlink.MainActivity
import com.commvault.commlink.R
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastShakeTimestamp = 0L
    private val shakeCooldownMs = 2000L // 2-second cooldown between detections
    private val shakeThreshold = 12.0f // m/s² acceleration threshold

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdateTime = 0L

    companion object {
        private const val CHANNEL_ID = "shake_detector_channel"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            val intent = Intent(context, ShakeDetectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShakeDetectorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        Log.d("ShakeDetector", "Service started")
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        Log.d("ShakeDetector", "Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTime = System.currentTimeMillis()

        // Throttle sensor reads to ~50ms intervals
        if (currentTime - lastUpdateTime < 50) return
        lastUpdateTime = currentTime

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate absolute acceleration magnitude
        val acceleration = sqrt(x * x + y * y + z * z)

        // Gravity is ~9.8 m/s^2. A shake threshold of 12 means 12 m/s^2 *above* gravity.
        // Let's check if the total acceleration exceeds gravity + threshold
        if (acceleration > (9.8f + shakeThreshold)) {
            if (currentTime - lastShakeTimestamp > shakeCooldownMs) {
                lastShakeTimestamp = currentTime
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onShakeDetected() {
        Log.d("ShakeDetector", "Shake detected! Launching app.")
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(launchIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shake Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shake to launch CommLink"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.commvault_logo)
            .setContentTitle("CommLink Shake Detection")
            .setContentText("Shake your phone to launch CommLink")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
