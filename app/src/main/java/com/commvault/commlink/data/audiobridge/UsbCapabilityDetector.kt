package com.commvault.commlink.data.audiobridge

import com.commvault.commlink.data.root.RootManager
import com.commvault.commlink.ui.audiobridge.model.CapabilityResult

class UsbCapabilityDetector {

    suspend fun checkSupport(): CapabilityResult {
        if (!RootManager.checkRootAvailability()) {
            return CapabilityResult.Unsupported("Root access denied or unavailable.")
        }

        // 1. Check ConfigFS
        val configFsCheck = RootManager.executeCommand("ls -d /sys/kernel/config/usb_gadget")
        if (configFsCheck == null || !configFsCheck.isSuccess) {
            return CapabilityResult.Unsupported("ConfigFS usb_gadget is not mounted or not supported by this kernel.")
        }

        // 2. Check UDC
        val udcCheck = RootManager.executeCommand("ls -1 /sys/class/udc/")
        if (udcCheck == null || !udcCheck.isSuccess || udcCheck.stdout.isBlank()) {
            return CapabilityResult.Unsupported("No USB Device Controller (UDC) found.")
        }

        // 3. Check UAC2 support via config.gz or probing (probing is safer, try to create a dummy uac2 function)
        // Since creating a function requires root and can fail safely if unsupported:
        val dummyCheck = RootManager.executeCommand(
            "mkdir /sys/kernel/config/usb_gadget/g_dummy_test && " +
            "mkdir /sys/kernel/config/usb_gadget/g_dummy_test/functions/uac2.0 && " +
            "rmdir /sys/kernel/config/usb_gadget/g_dummy_test/functions/uac2.0 && " +
            "rmdir /sys/kernel/config/usb_gadget/g_dummy_test"
        )
        
        val hasUac2 = dummyCheck?.isSuccess == true

        if (!hasUac2) {
            // Try UAC1
            val uac1Check = RootManager.executeCommand(
                "mkdir /sys/kernel/config/usb_gadget/g_dummy_test && " +
                "mkdir /sys/kernel/config/usb_gadget/g_dummy_test/functions/uac1.0 && " +
                "rmdir /sys/kernel/config/usb_gadget/g_dummy_test/functions/uac1.0 && " +
                "rmdir /sys/kernel/config/usb_gadget/g_dummy_test"
            )
            val hasUac1 = uac1Check?.isSuccess == true
            
            if (!hasUac1) {
                return CapabilityResult.Unsupported("Kernel lacks UAC1 and UAC2 USB audio gadget support.")
            }
            return CapabilityResult.PartiallySupported("Only UAC1 audio is supported. Bi-directional might be limited.")
        }

        return CapabilityResult.Supported
    }

    suspend fun getDiagnostics(): String {
        val sb = java.lang.StringBuilder()
        sb.append("ConfigFS: ${RootManager.executeCommand("ls -d /sys/kernel/config/usb_gadget")?.isSuccess == true}\n")
        sb.append("UDC: ${RootManager.executeCommand("ls -1 /sys/class/udc/")?.stdout?.replace("\n", ", ") ?: "None"}\n")
        sb.append("Current Config: ${RootManager.executeCommand("getprop sys.usb.config")?.stdout}\n")
        return sb.toString()
    }
}
