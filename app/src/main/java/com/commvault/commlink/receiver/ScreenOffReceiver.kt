package com.commvault.commlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.domain.model.HidKeyCodes

/**
 * Listens for ACTION_SCREEN_OFF (phone power button press) and sends Win+L
 * to lock the connected PC via Bluetooth HID.
 *
 * This receiver is registered/unregistered dynamically from the ViewModel
 * based on the user's "Power Button Lock" setting.
 */
class ScreenOffReceiver(
    private val hidDeviceManager: HidDeviceManager
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenOffReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            Log.d(TAG, "Screen OFF detected — sending Win+L to lock PC")

            // Only send if we're actually connected to a PC
            val state = hidDeviceManager.connectionState.value
            if (state is HidDeviceManager.ConnectionState.Connected) {
                hidDeviceManager.sendKeyPress(
                    HidKeyCodes.KEY_L,
                    HidKeyCodes.MODIFIER_LEFT_GUI,
                    useSticky = false
                )
                Log.d(TAG, "Win+L sent successfully")
            } else {
                Log.d(TAG, "Not connected to any PC, skipping lock command")
            }
        }
    }
}
