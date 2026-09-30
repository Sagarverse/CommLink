package com.commvault.commlink.data.bluetooth

import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.commvault.commlink.MainActivity
import com.commvault.commlink.data.secure.SecureStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class HidService : Service() {

    private lateinit var hidDeviceManager: HidDeviceManager
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val channelId = "commlink_service_channel"
    
    private var notificationStateTitle: String = "Disconnected"
    private var notificationStateText: String = "Bluetooth HID remote connection is inactive."

    inner class LocalBinder : Binder() {
        fun getService(): HidService = this@HidService
    }

    override fun onCreate() {
        super.onCreate()
        hidDeviceManager = HidDeviceManager.getInstance(this)

        createNotificationChannel()
        val prefs = getSharedPreferences("commlink_settings", Context.MODE_PRIVATE)
        val showNotification = prefs.getBoolean("persistent_notification", true)

        if (showNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1, 
                    buildNotification("Disconnected", "Bluetooth HID connection is inactive."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(1, buildNotification("Disconnected", "Bluetooth HID connection is inactive."))
            }
        }
        observeConnectionState()
    }

    private fun observeConnectionState() {
        serviceScope.launch {
            combine(
                hidDeviceManager.connectionState,
                hidDeviceManager.isTextPushing,
                hidDeviceManager.isPushPaused
            ) { state, isPushing, isPaused ->
                val (title, text) = when (state) {
                    is HidDeviceManager.ConnectionState.Connected -> {
                        if (isPushing) {
                            (if (isPaused) "Typing Paused" else "Typing...") to "Active connection to ${state.deviceName}"
                        } else {
                            "Connected" to "Active connection to ${state.deviceName}"
                        }
                    }
                    is HidDeviceManager.ConnectionState.Connecting ->
                        "Connecting" to "Attempting to connect..."
                    else -> {
                        "Disconnected" to "Bluetooth HID connection is inactive."
                    }
                }
                updateNotification(title, text)
            }.collect {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "LOCK_WINDOWS" -> {
                hidDeviceManager.lockWindows()
                Toast.makeText(this, "Lock command sent", Toast.LENGTH_SHORT).show()
            }
            "UNLOCK_WINDOWS", "UNLOCK_WINDOWS_NORMAL" -> {
                val password = SecureStorage(applicationContext).getCommvaultPassword() ?: ""
                if (password.isNotEmpty()) {
                    hidDeviceManager.unlockWindows(password, wakeScreenFirst = true)
                    Toast.makeText(this, "Unlocking Windows (Normal)...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Save password in dashboard first", Toast.LENGTH_SHORT).show()
                }
            }
            "UNLOCK_WINDOWS_DIRECT" -> {
                val password = SecureStorage(applicationContext).getCommvaultPassword() ?: ""
                if (password.isNotEmpty()) {
                    hidDeviceManager.unlockWindows(password, wakeScreenFirst = false)
                    Toast.makeText(this, "Unlocking Windows (Direct)...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Save password in dashboard first", Toast.LENGTH_SHORT).show()
                }
            }
            "STOP_APP" -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            "UPDATE_FOREGROUND" -> {
                val showNotification = getSharedPreferences("commlink_settings", Context.MODE_PRIVATE).getBoolean("persistent_notification", true)
                if (showNotification) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1, buildNotification(notificationStateTitle, notificationStateText), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                    } else {
                        startForeground(1, buildNotification(notificationStateTitle, notificationStateText))
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                channelId, "CommLink Background Service", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains Bluetooth HID connection active in background."
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        notificationStateTitle = title
        notificationStateText = text

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val lockIntent = Intent(this, HidService::class.java).apply { action = "LOCK_WINDOWS" }
        val lockPending = PendingIntent.getService(this, 10, lockIntent, PendingIntent.FLAG_IMMUTABLE)

        val unlockIntent = Intent(this, HidService::class.java).apply { action = "UNLOCK_WINDOWS_NORMAL" }
        val unlockPending = PendingIntent.getService(this, 20, unlockIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, HidService::class.java).apply { action = "STOP_APP" }
        val stopPending = PendingIntent.getService(this, 30, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CommLink Hub: $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            
        builder.addAction(com.commvault.commlink.R.drawable.ic_qs_unlock, "Unlock", unlockPending)
        builder.addAction(com.commvault.commlink.R.drawable.ic_qs_lock, "Lock", lockPending)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", stopPending)
            
        val notification = builder.build()
        notification.flags = notification.flags or android.app.Notification.FLAG_NO_CLEAR or android.app.Notification.FLAG_ONGOING_EVENT
        return notification
    }

    private fun updateNotification(title: String, text: String) {
        val showNotification = getSharedPreferences("commlink_settings", Context.MODE_PRIVATE).getBoolean("persistent_notification", true)
        if (showNotification) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(1, buildNotification(title, text))
        }
    }

    val connectionState: StateFlow<HidDeviceManager.ConnectionState> get() = hidDeviceManager.connectionState

    fun connect(device: BluetoothDevice) = hidDeviceManager.connect(device)
    fun disconnect() = hidDeviceManager.disconnect()
    fun sendKey(keyCode: Byte, modifier: Byte) = hidDeviceManager.sendKeyPress(keyCode, modifier)
    fun sendText(text: String) = hidDeviceManager.sendText(text)
    fun lockWindows() = hidDeviceManager.lockWindows()
    fun unlockWindows(password: String) = hidDeviceManager.unlockWindows(password, wakeScreenFirst = true)

    override fun onDestroy() {
        serviceScope.cancel()
        hidDeviceManager.unregister()
        super.onDestroy()
    }
}
