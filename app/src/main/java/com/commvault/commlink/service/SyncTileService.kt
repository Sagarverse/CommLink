package com.commvault.commlink.service

import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.commvault.commlink.R
import com.commvault.commlink.data.bluetooth.HidDeviceManager

class SyncTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.icon = Icon.createWithResource(this, R.drawable.ic_qs_sync)
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    HidDeviceManager.getInstance(applicationContext).sendText(text)
                    Toast.makeText(applicationContext, "Clipboard text sent to PC!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Failed to sync: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
        
        Handler(Looper.getMainLooper()).postDelayed({
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        }, 500)
    }
}
