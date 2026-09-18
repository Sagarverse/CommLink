package com.commvault.commlink.data.hid

import android.util.Log
import com.commvault.commlink.data.root.RootManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataOutputStream

class UsbHidTransport : HidTransport {
    private val _connectionState = MutableStateFlow<HidTransport.ConnectionState>(HidTransport.ConnectionState.Disconnected)
    override val connectionState: StateFlow<HidTransport.ConnectionState> = _connectionState.asStateFlow()

    override var isReady: Boolean = false
        private set

    private var rootProcess: Process? = null
    private var outputStream: DataOutputStream? = null

    override fun connect() {
        if (_connectionState.value is HidTransport.ConnectionState.Connected) return
        _connectionState.value = HidTransport.ConnectionState.Connecting

        CoroutineScope(Dispatchers.IO).launch {
            val success = configureGadget()
            if (success) {
                // Open interactive root shell for writing binary reports fast
                try {
                    rootProcess = Runtime.getRuntime().exec("su")
                    outputStream = DataOutputStream(rootProcess!!.outputStream)
                    isReady = true
                    _connectionState.value = HidTransport.ConnectionState.Connected("USB CommLink HID")
                } catch (e: Exception) {
                    Log.e("UsbHidTransport", "Failed to start root shell for streaming", e)
                    _connectionState.value = HidTransport.ConnectionState.Disconnected
                }
            } else {
                _connectionState.value = HidTransport.ConnectionState.Disconnected
            }
        }
    }

    override fun disconnect() {
        isReady = false
        try {
            outputStream?.writeBytes("exit\n")
            outputStream?.flush()
            outputStream?.close()
            rootProcess?.destroy()
        } catch (e: Exception) {}
        
        outputStream = null
        rootProcess = null
        
        CoroutineScope(Dispatchers.IO).launch {
            restoreOriginalGadget()
        }
        _connectionState.value = HidTransport.ConnectionState.Disconnected
    }

    override fun sendReportInternal(id: Int, data: ByteArray) {
        if (!isReady || outputStream == null) return
        val targetNode = when(id) {
            1 -> "/dev/hidg0" // Keyboard
            3 -> "/dev/hidg1" // Mouse
            4 -> "/dev/hidg2" // Consumer
            else -> return
        }
        try {
            val hex = data.joinToString("") { "\\x%02x".format(it) }
            outputStream?.writeBytes("printf \"%b\" \"$hex\" > $targetNode\n")
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e("UsbHidTransport", "Failed to send report to $targetNode", e)
        }
    }

    private suspend fun configureGadget(): Boolean {
        val udcCheck = RootManager.executeCommand("ls -1 /sys/class/udc/")
        val udcName = udcCheck?.stdout?.lines()?.firstOrNull { it.isNotBlank() }
        if (udcName == null) return false

        val base = "/config/usb_gadget/g1"
        val script = """
            CONF=${'$'}(ls -1 $base/configs | head -n 1)
            if [ -z "${'$'}CONF" ]; then CONF="b.1"; mkdir -p $base/configs/${'$'}CONF; fi
            
            # Temporarily unbind native gadget
            echo "" > $base/UDC 2>/dev/null
            sleep 0.5
            
            # Clean up old state if exists
            rm $base/configs/${'$'}CONF/hid.usb0 2>/dev/null
            rm $base/configs/${'$'}CONF/hid.usb1 2>/dev/null
            rm $base/configs/${'$'}CONF/hid.usb2 2>/dev/null
            rmdir $base/functions/hid.usb0 2>/dev/null
            rmdir $base/functions/hid.usb1 2>/dev/null
            rmdir $base/functions/hid.usb2 2>/dev/null
            
            mkdir -p $base/functions/hid.usb0
            echo 1 > $base/functions/hid.usb0/protocol
            echo 1 > $base/functions/hid.usb0/subclass
            echo 8 > $base/functions/hid.usb0/report_length
            printf "%b" "${getHex(KEYBOARD_DESC)}" > $base/functions/hid.usb0/report_desc
            ln -s $base/functions/hid.usb0 $base/configs/${'$'}CONF/

            mkdir -p $base/functions/hid.usb1
            echo 2 > $base/functions/hid.usb1/protocol
            echo 0 > $base/functions/hid.usb1/subclass
            echo 4 > $base/functions/hid.usb1/report_length
            printf "%b" "${getHex(MOUSE_DESC)}" > $base/functions/hid.usb1/report_desc
            ln -s $base/functions/hid.usb1 $base/configs/${'$'}CONF/

            mkdir -p $base/functions/hid.usb2
            echo 0 > $base/functions/hid.usb2/protocol
            echo 0 > $base/functions/hid.usb2/subclass
            echo 1 > $base/functions/hid.usb2/report_length
            printf "%b" "${getHex(CONSUMER_DESC)}" > $base/functions/hid.usb2/report_desc
            ln -s $base/functions/hid.usb2 $base/configs/${'$'}CONF/

            # Re-bind native gadget with HID injected
            echo "$udcName" > $base/UDC
        """.trimIndent()

        val res = RootManager.executeCommand(script)
        if (res == null || !res.isSuccess) {
            Log.e("UsbHidTransport", "Failed to configure gadget. Error: ${res?.stderr}")
            restoreOriginalGadget(udcName)
            return false
        }
        
        // Wait for OS to mount character devices
        kotlinx.coroutines.delay(1000)
        return true
    }

    private suspend fun restoreOriginalGadget(udcName: String? = null) {
        val udc = udcName ?: RootManager.executeCommand("ls -1 /sys/class/udc/")?.stdout?.lines()?.firstOrNull { it.isNotBlank() } ?: return
        
        val base = "/config/usb_gadget/g1"
        val script = """
            CONF=${'$'}(ls -1 $base/configs | head -n 1)
            if [ -z "${'$'}CONF" ]; then CONF="b.1"; fi
            
            echo "" > $base/UDC 2>/dev/null
            sleep 0.5
            
            rm $base/configs/${'$'}CONF/hid.usb0 2>/dev/null
            rm $base/configs/${'$'}CONF/hid.usb1 2>/dev/null
            rm $base/configs/${'$'}CONF/hid.usb2 2>/dev/null
            rmdir $base/functions/hid.usb0 2>/dev/null
            rmdir $base/functions/hid.usb1 2>/dev/null
            rmdir $base/functions/hid.usb2 2>/dev/null
            
            echo "$udc" > $base/UDC
        """.trimIndent()
        
        RootManager.executeCommand(script)
    }

    private fun getHex(arr: ByteArray): String {
        return arr.joinToString("") { "\\\\x%02x".format(it) }
    }

    companion object {
        val KEYBOARD_DESC = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x06.toByte(), 0xA1.toByte(), 0x01.toByte(),
            0x05.toByte(), 0x07.toByte(), 0x19.toByte(), 0xE0.toByte(), 0x29.toByte(), 0xE7.toByte(),
            0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x08.toByte(), 0x81.toByte(), 0x02.toByte(), 0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x08.toByte(), 0x81.toByte(), 0x03.toByte(), 0x95.toByte(), 0x06.toByte(),
            0x75.toByte(), 0x08.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x65.toByte(),
            0x05.toByte(), 0x07.toByte(), 0x19.toByte(), 0x00.toByte(), 0x29.toByte(), 0x65.toByte(),
            0x81.toByte(), 0x00.toByte(), 0xC0.toByte()
        )
        val MOUSE_DESC = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x02.toByte(), 0xA1.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x01.toByte(), 0xA1.toByte(), 0x00.toByte(), 0x05.toByte(), 0x09.toByte(),
            0x19.toByte(), 0x01.toByte(), 0x29.toByte(), 0x03.toByte(), 0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(), 0x95.toByte(), 0x03.toByte(), 0x75.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x02.toByte(), 0x95.toByte(), 0x01.toByte(), 0x75.toByte(), 0x05.toByte(),
            0x81.toByte(), 0x03.toByte(), 0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x30.toByte(),
            0x09.toByte(), 0x31.toByte(), 0x09.toByte(), 0x38.toByte(), 0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F.toByte(), 0x75.toByte(), 0x08.toByte(), 0x95.toByte(), 0x03.toByte(),
            0x81.toByte(), 0x06.toByte(), 0xC0.toByte(), 0xC0.toByte()
        )
        val CONSUMER_DESC = byteArrayOf(
            0x05.toByte(), 0x0C.toByte(), 0x09.toByte(), 0x01.toByte(), 0xA1.toByte(), 0x01.toByte(),
            0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x07.toByte(), 0x09.toByte(), 0xB5.toByte(), 0x09.toByte(), 0xB6.toByte(),
            0x09.toByte(), 0xB7.toByte(), 0x09.toByte(), 0xCD.toByte(), 0x09.toByte(), 0xE2.toByte(),
            0x09.toByte(), 0xEA.toByte(), 0x09.toByte(), 0xE9.toByte(), 0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(), 0x81.toByte(), 0x03.toByte(), 0xC0.toByte()
        )
    }
}
