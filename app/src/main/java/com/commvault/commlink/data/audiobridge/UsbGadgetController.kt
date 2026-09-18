package com.commvault.commlink.data.audiobridge

import android.util.Log
import com.commvault.commlink.data.root.RootManager

class UsbGadgetController {

    suspend fun configureAudioGadget(): Boolean {
        Log.i("UsbGadgetController", "Attempting to configure USB Audio Gadget")

        val udcCheck = RootManager.executeCommand("ls -1 /sys/class/udc/")
        val udcName = udcCheck?.stdout?.lines()?.firstOrNull { it.isNotBlank() }
        
        if (udcName == null) {
            Log.e("UsbGadgetController", "Failed to find UDC")
            return false
        }

        val base = "/config/usb_gadget/g1"
        val script = """
            CONF=${'$'}(ls -1 $base/configs | head -n 1)
            if [ -z "${'$'}CONF" ]; then CONF="b.1"; mkdir -p $base/configs/${'$'}CONF; fi
            
            # Temporarily unbind native gadget
            echo "" > $base/UDC 2>/dev/null
            sleep 0.5
            
            # Clean up old state if exists
            rm $base/configs/${'$'}CONF/uac2.0 2>/dev/null
            rmdir $base/functions/uac2.0 2>/dev/null
            
            # Create UAC2 function
            mkdir -p $base/functions/uac2.0
            echo 3 > $base/functions/uac2.0/p_chmask
            echo 3 > $base/functions/uac2.0/c_chmask
            echo 48000 > $base/functions/uac2.0/p_srate
            echo 48000 > $base/functions/uac2.0/c_srate
            
            # Link function to config
            ln -s $base/functions/uac2.0 $base/configs/${'$'}CONF/
            
            # Re-bind to UDC
            echo "$udcName" > $base/UDC
        """.trimIndent()

        val result = RootManager.executeCommand(script)
        if (result == null || !result.isSuccess) {
            Log.e("UsbGadgetController", "Failed to configure Audio Gadget. Error: ${result?.stderr}")
            restoreOriginalGadget(udcName)
            return false
        }

        // Wait for OS to mount character devices
        kotlinx.coroutines.delay(1000)
        return true
    }

    suspend fun restoreOriginalGadget(udcName: String? = null) {
        val udc = udcName ?: RootManager.executeCommand("ls -1 /sys/class/udc/")?.stdout?.lines()?.firstOrNull { it.isNotBlank() } ?: return
        
        val base = "/config/usb_gadget/g1"
        val script = """
            CONF=${'$'}(ls -1 $base/configs | head -n 1)
            if [ -z "${'$'}CONF" ]; then CONF="b.1"; fi
            
            echo "" > $base/UDC 2>/dev/null
            sleep 0.5
            
            rm $base/configs/${'$'}CONF/uac2.0 2>/dev/null
            rmdir $base/functions/uac2.0 2>/dev/null
            
            echo "$udc" > $base/UDC
        """.trimIndent()
        
        RootManager.executeCommand(script)
    }
}
