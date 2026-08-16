package com.commvault.commlink.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class GithubRelease(
    val tag_name: String,
    val assets: List<GithubAsset>
)

data class GithubAsset(
    val name: String,
    val browser_download_url: String
)

class GithubUpdater(private val context: Context) {
    // We will use a placeholder or best guess repository name
    private val repoOwner = "Sagar-M"
    private val repoName = "CommLink"
    private val currentVersion = "1.0" // Ideally from BuildConfig.VERSION_NAME

    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == 200) {
                    val reader = InputStreamReader(connection.inputStream)
                    val release = Gson().fromJson(reader, GithubRelease::class.java)
                    reader.close()

                    val latestVersion = release.tag_name.removePrefix("v")
                    if (latestVersion != currentVersion) {
                        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                        if (apkAsset != null) {
                            downloadUpdate(apkAsset.browser_download_url, apkAsset.name)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GithubUpdater", "Failed to check for updates", e)
            }
        }
    }

    private fun downloadUpdate(downloadUrl: String, fileName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)
        
        val request = DownloadManager.Request(uri)
            .setTitle("CommLink Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installUpdate(fileName)
                    context.unregisterReceiver(this)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installUpdate(fileName: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", // Make sure this is declared in AndroidManifest
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("GithubUpdater", "Failed to install update", e)
        }
    }
}
