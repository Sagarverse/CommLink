package com.commvault.commlink.data.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AutoUpdater(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1L

    fun checkForUpdatesAndDownload(
        apkUrl: String = "https://raw.githubusercontent.com/github/android/main/README.md" // Dummy URL for testing
    ) {
        Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()

        // Simulate network API call to check version
        CoroutineScope(Dispatchers.IO).launch {
            delay(1500) // Fake API latency
            
            // New version found! Start background download
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("CommLink Update")
                .setDescription("Downloading new version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "commlink_update.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            // Register receiver for when download finishes
            context.registerReceiver(
                onDownloadComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )

            // Cleanup old file if it exists
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "commlink_update.apk")
            if (file.exists()) file.delete()

            downloadId = downloadManager.enqueue(request)
        }
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                // Download finished, prompt install
                promptInstall(context)
                try {
                    context.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.e("AutoUpdater", "Failed to unregister receiver", e)
                }
            }
        }
    }

    private fun promptInstall(context: Context) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "commlink_update.apk")
            if (!file.exists()) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("AutoUpdater", "Failed to start installer", e)
            Toast.makeText(context, "Failed to start installer", Toast.LENGTH_SHORT).show()
        }
    }
}
