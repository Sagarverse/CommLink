package com.commvault.commlink.data.audiobridge

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.commvault.commlink.ui.audiobridge.model.CapabilityResult

class BluetoothCapabilityDetector(private val context: Context) {

    fun checkSupport(): CapabilityResult {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return CapabilityResult.Unsupported("Bluetooth is off or not supported.")
        }

        // To support Laptop -> Phone AI (Android receives audio), we need A2DP SINK.
        // A2DP Sink is typically only exposed on Android TVs or automotive unless the ROM is modified.
        // We will check if the profile constant exists.
        val hasA2dpSink = try {
            val a2dpSinkClass = Class.forName("android.bluetooth.BluetoothA2dpSink")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        // For Phone AI -> Laptop (Laptop receives phone mic), we need HFP AG (Audio Gateway) 
        // to act as a hands-free device, or Windows needs to support standard Bluetooth mic profiles.
        // HFP Client (Hands-Free Client) is also often hidden.
        val hasHfpClient = try {
            val hfpClientClass = Class.forName("android.bluetooth.BluetoothHeadsetClient")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        if (hasA2dpSink && hasHfpClient) {
            return CapabilityResult.Supported
        } else if (hasA2dpSink) {
            return CapabilityResult.PartiallySupported("Can receive laptop audio, but cannot send phone mic to laptop (Missing HFP Client).")
        } else if (hasHfpClient) {
            return CapabilityResult.PartiallySupported("Can send phone mic to laptop, but cannot receive laptop audio (Missing A2DP Sink).")
        } else {
            return CapabilityResult.Unsupported("This device's Bluetooth stack does not support A2DP Sink or HFP Client roles.")
        }
    }

    fun getDiagnostics(): String {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) return "No Bluetooth Adapter"
        
        val a2dpSink = try { Class.forName("android.bluetooth.BluetoothA2dpSink"); "Available" } catch (e: Exception) { "Missing" }
        val hfpClient = try { Class.forName("android.bluetooth.BluetoothHeadsetClient"); "Available" } catch (e: Exception) { "Missing" }
        
        return "BT Address: ${bluetoothAdapter.address}\nA2DP Sink: $a2dpSink\nHFP Client: $hfpClient"
    }
}
