package com.commvault.commlink.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.commvault.commlink.data.bluetooth.HidService

import android.graphics.drawable.Icon
import com.commvault.commlink.R

class DirectUnlockTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.icon = Icon.createWithResource(this, R.drawable.ic_qs_direct_unlock)
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(applicationContext, HidService::class.java).apply {
            action = "UNLOCK_WINDOWS_DIRECT"
        }
        startService(intent)
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        }, 500)
    }
}
