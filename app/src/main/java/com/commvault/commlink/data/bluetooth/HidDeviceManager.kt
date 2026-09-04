package com.commvault.commlink.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class HidDeviceManager private constructor(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var hidDevice: BluetoothHidDevice? = null
    var connectedDevice: BluetoothDevice? = null
        private set
    private var deviceRepository: com.commvault.commlink.domain.repository.DeviceRepository? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var reconnectJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var isManuallyDisconnected = false
    private var pendingBondAddress: String? = null
    private var pendingConnectRetries: Int = 3
    private var pendingRetryDelayMs: Long = 1500

    private var isAppRegistered = false
    private val registrationLock = Any()

    private data class ReportRequest(val id: Int, val data: ByteArray)
    private val reportChannel = Channel<ReportRequest>(Channel.UNLIMITED)

    var typingDelay = 120L 
    private var currentModifiers: Byte = 0
    private var textPushJob: Job? = null

    private val _isPushPaused = MutableStateFlow(false)
    val isPushPaused: StateFlow<Boolean> = _isPushPaused.asStateFlow()

    private val _currentModifiers = MutableStateFlow<Byte>(0)
    val activeModifiers: StateFlow<Byte> = _currentModifiers.asStateFlow()

    private val _isTextPushing = MutableStateFlow(false)
    val isTextPushing: StateFlow<Boolean> = _isTextPushing.asStateFlow()

    private var mouseAccumX = 0f
    private var mouseAccumY = 0f
    private var lastButtons = 0
    var isMouseLocked = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    handleBluetoothOff()
                } else if (state == BluetoothAdapter.STATE_ON) {
                    initProfiles()
                }
            }
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

            val device = intent.parcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (pendingBondAddress != device.address) return

            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

            when (bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    pendingBondAddress = null
                    connectWithRetry(device, pendingConnectRetries, pendingRetryDelayMs)
                }
                BluetoothDevice.BOND_NONE -> {
                    if (previousBondState == BluetoothDevice.BOND_BONDING) {
                        pendingBondAddress = null
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }
    }

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d("HidDeviceManager", "HID profile connected")
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
                checkCurrentConnections()
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            try {
                Log.d("HidDeviceManager", "Connection state changed: $state for device ${device?.address}")
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        reconnectJob?.cancel()
                        connectionTimeoutJob?.cancel()
                        connectedDevice = device
                        _connectionState.value = ConnectionState.Connected(device?.name ?: "Unknown")
                        
                        device?.let { 
                            deviceRepository?.saveWorkstation(it.address, it.name ?: "Unknown Workstation")
                        }
                        
                        isManuallyDisconnected = false
                        scope.launch {
                            delay(250)
                            sendNeutralReports()
                            delay(550)
                            sendNeutralReports()
                        } 
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevice = null
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            } catch (e: Exception) {
                Log.e("HidDeviceManager", "Error in onConnectionStateChanged", e)
            }
        }

        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(device, registered)
            Log.d("HidDeviceManager", "App registration status: $registered")
            synchronized(registrationLock) {
                isAppRegistered = registered
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondStateReceiver, bondFilter)
        initProfiles()
        
        deviceRepository = com.commvault.commlink.data.repository.DeviceRepositoryImpl(context)
        
        scope.launch {
            for (request in reportChannel) {
                sendReportInternal(request.id, request.data)
                // Removed delay(12) to eliminate trackpad latency and prevent queue buildup on high refresh rate displays.
            }
        }
    }

    private fun handleBluetoothOff() {
        reconnectJob?.cancel()
        connectedDevice = null
        _connectionState.value = ConnectionState.Disconnected
        hidDevice = null
    }

    fun initProfiles() {
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
        }
    }

    private fun registerApp() {
        if (bluetoothAdapter?.isEnabled != true) return
        scope.launch(Dispatchers.IO) {
            try {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    if (bluetoothAdapter.name != "CommLink Keyboard & Mouse") {
                        bluetoothAdapter.name = "CommLink Keyboard & Mouse"
                    }
                }
                
                val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                    "CommLink Keyboard & Mouse", "CommLink Bluetooth Remote", "CommLink",
                    0xC0.toByte(), // Keyboard + Mouse
                    HID_REPORT_DESCRIPTOR
                )
                
                Log.d("HidDeviceManager", "Registering HID app...")
                hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
            } catch (e: Exception) {
                Log.e("HidDeviceManager", "Failed to register HID app", e)
            }
        }
    }

    fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun connect(device: BluetoothDevice) {
        if (bluetoothAdapter?.isEnabled != true) return
        isManuallyDisconnected = false
        _connectionState.value = ConnectionState.Connecting
        
        scope.launch(Dispatchers.IO) {
            ensureHidProfileReady()
            
            connectedDevice?.let { activeDev ->
                if (activeDev.address != device.address) {
                    hidDevice?.disconnect(activeDev)
                    delay(600)
                }
            }

            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                pendingBondAddress = device.address
                pendingConnectRetries = 3
                pendingRetryDelayMs = 1500
                try {
                    val startedBonding = device.createBond()
                    if (!startedBonding && device.bondState != BluetoothDevice.BOND_BONDING) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Disconnected
                }
                return@launch
            }

            try {
                hidDevice?.unregisterApp()
                delay(800)
                registerApp()
                delay(800)
            } catch (e: Exception) {
                Log.e("HidDeviceManager", "Failed to cycle HID app registration", e)
            }

            hidDevice?.connect(device)
            
            connectionTimeoutJob?.cancel()
            connectionTimeoutJob = launch {
                delay(8000)
                if (_connectionState.value is ConnectionState.Connecting) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    fun connectWithRetry(device: BluetoothDevice, maxRetries: Int = 3, retryDelayMs: Long = 1500) {
        if (bluetoothAdapter?.isEnabled != true) return
        isManuallyDisconnected = false
        _connectionState.value = ConnectionState.Connecting
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            ensureHidProfileReady()

            connectedDevice?.let { activeDev ->
                if (activeDev.address != device.address) {
                    hidDevice?.disconnect(activeDev)
                    delay(600)
                }
            }

            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                pendingBondAddress = device.address
                pendingConnectRetries = maxRetries
                pendingRetryDelayMs = retryDelayMs
                try {
                    val startedBonding = device.createBond()
                    if (!startedBonding && device.bondState != BluetoothDevice.BOND_BONDING) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Disconnected
                }
                return@launch
            }

            repeat(maxRetries) { attempt ->
                if (_connectionState.value is ConnectionState.Connected) return@launch
                Log.d("HidDeviceManager", "Connection attempt ${attempt + 1}/$maxRetries for ${device.name}")
                hidDevice?.connect(device)
                delay(retryDelayMs)
            }
            delay(3000)
            if (_connectionState.value !is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    fun disconnect() {
        isManuallyDisconnected = true
        reconnectJob?.cancel()
        connectionTimeoutJob?.cancel()
        textPushJob?.cancel()
        runCatching { sendNeutralReports() }
        connectedDevice?.let { hidDevice?.disconnect(it) }
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun sendNeutralReports() {
        sendReportInternal(1, ByteArray(8))
        sendReportInternal(3, ByteArray(4))
    }

    private fun sendReportInternal(id: Int, data: ByteArray) {
        val device = connectedDevice ?: return
        if (bluetoothAdapter?.isEnabled != true) return
        try {
            val success = hidDevice?.sendReport(device, id, data) ?: false
            if (!success) Log.e("HidDeviceManager", "Failed to send report $id")
        } catch (e: Exception) {
            Log.e("HidDeviceManager", "Error sending report", e)
        }
    }

    fun setModifier(modifier: Byte, active: Boolean) {
        currentModifiers = if (active) {
            currentModifiers or modifier
        } else {
            currentModifiers and modifier.inv()
        }
        _currentModifiers.value = currentModifiers
        val report = ByteArray(8).apply { this[0] = currentModifiers }
        reportChannel.trySend(ReportRequest(1, report))
    }

    fun sendMediaKey(bits: Byte) {
        if (connectionState.value !is ConnectionState.Connected) return
        // Send key down
        reportChannel.trySend(ReportRequest(4, byteArrayOf(bits)))
        // Send key up immediately for media keys
        reportChannel.trySend(ReportRequest(4, byteArrayOf(0)))
    }

    fun sendKeyPress(keyCode: Byte, modifier: Byte = 0, useSticky: Boolean = true) {
        val effectiveModifier = if (modifier != 0.toByte()) {
            modifier or if (useSticky) currentModifiers else 0
        } else if (useSticky) {
            currentModifiers
        } else {
            0.toByte()
        }
        
        val pressReport = ByteArray(8).apply { 
            this[0] = effectiveModifier
            this[2] = keyCode 
        }
        reportChannel.trySend(ReportRequest(1, pressReport))
        
        scope.launch { 
            delay(50)
            if (useSticky && currentModifiers != 0.toByte()) {
                currentModifiers = 0
                _currentModifiers.value = 0
            }
            val releaseReport = ByteArray(8).apply { 
                this[0] = if (useSticky) currentModifiers else 0 
            }
            reportChannel.trySend(ReportRequest(1, releaseReport)) 
        }
    }

    fun resetMouseAccumulator() {
        mouseAccumX = 0f
        mouseAccumY = 0f
    }

    fun sendMouseMove(dx: Float, dy: Float, buttons: Int = 0, wheel: Int = 0) {
        if (isMouseLocked) {
            mouseAccumX = 0f
            mouseAccumY = 0f
            return
        }
        mouseAccumX += dx
        mouseAccumY += dy

        val outX = mouseAccumX.roundToInt()
        val outY = mouseAccumY.roundToInt()

        val buttonsChanged = buttons != lastButtons

        if (outX != 0 || outY != 0 || buttonsChanged || wheel != 0) {
            lastButtons = buttons
            mouseAccumX -= outX
            mouseAccumY -= outY

            val report = ByteArray(4).apply {
                this[0] = buttons.toByte()
                this[1] = outX.coerceIn(-127, 127).toByte()
                this[2] = outY.coerceIn(-127, 127).toByte()
                this[3] = wheel.coerceIn(-127, 127).toByte()
            }
            reportChannel.trySend(ReportRequest(3, report))
        }
    }

    fun sendMouseReportWithResult(dx: Float, dy: Float, buttons: Int = 0, wheel: Int = 0): Boolean {
        val device = connectedDevice ?: return false
        if (bluetoothAdapter?.isEnabled != true) return false
        val outX = dx.roundToInt().coerceIn(-127, 127)
        val outY = dy.roundToInt().coerceIn(-127, 127)
        val report = ByteArray(4).apply {
            this[0] = buttons.toByte()
            this[1] = outX.toByte()
            this[2] = outY.toByte()
            this[3] = wheel.coerceIn(-127, 127).toByte()
        }
        return try {
            hidDevice?.sendReport(device, 3, report) ?: false
        } catch (e: Exception) {
            Log.e("HidDeviceManager", "sendMouseReportWithResult failed", e)
            false
        }
    }

    fun sendText(text: String): kotlinx.coroutines.Job? {
        textPushJob?.cancel()
        _isPushPaused.value = false
        _isTextPushing.value = true
        textPushJob = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                text.forEach { char ->
                    while (_isPushPaused.value) {
                        delay(100)
                    }

                    val model = com.commvault.commlink.domain.model.HidKeyCodes.getHidCode(char)
                    if (model.keyCode != 0.toByte() || model.modifier != 0.toByte()) {
                        // 1. Send Key Press
                        val pressReport = ByteArray(8).apply { 
                            this[0] = model.modifier
                            this[2] = model.keyCode 
                        }
                        reportChannel.trySend(ReportRequest(1, pressReport))
                        
                        // 2. Wait to ensure the OS registers the press
                        delay(20)
                        
                        // 3. Send Key Release
                        val releaseReport = ByteArray(8)
                        reportChannel.trySend(ReportRequest(1, releaseReport))
                        
                        // 4. Wait before typing the next character
                        delay(typingDelay) 
                    }
                }
            } finally {
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                if (elapsedMs < 600L) {
                    delay(600L - elapsedMs)
                }
                _isTextPushing.value = false
            }
        }
        return textPushJob
    }

    fun stopTextPush() {
        textPushJob?.cancel()
        _isPushPaused.value = false
        _isTextPushing.value = false
        reportChannel.trySend(ReportRequest(1, ByteArray(8)))
    }
    
    fun toggleTextPushPause() {
        _isPushPaused.value = !_isPushPaused.value
    }

    fun lockWindows() {
        // Send Win+L (MODIFIER_LEFT_GUI | KEY_L) to lock Windows
        sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_L, com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_GUI, useSticky = false)
    }

    fun unlockWindows(password: String, wakeScreenFirst: Boolean = true) {
        scope.launch {
            if (wakeScreenFirst) {
                // Wake the screen and dismiss lock screen
                sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_ENTER, useSticky = false)
                delay(1000L) // Wait for transition animation
            }
            sendText(password)?.join()
            delay(200L)
            sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_ENTER, useSticky = false)
        }
    }

    private fun checkCurrentConnections() {
        val devices = hidDevice?.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTED))
        if (!devices.isNullOrEmpty()) {
            connectedDevice = devices[0]
            _connectionState.value = ConnectionState.Connected(connectedDevice?.name ?: "Unknown")
        }
    }

    fun unregister() {
        try { context.unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) { }
        try { context.unregisterReceiver(bondStateReceiver) } catch (e: Exception) { }
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
    }

    private suspend fun ensureHidProfileReady(timeoutMs: Long = 4000) {
        if (hidDevice != null && isAppRegistered) return
        
        if (hidDevice == null) {
            initProfiles()
        }
        
        val deadline = System.currentTimeMillis() + timeoutMs
        var registrationAttempted = false
        while (System.currentTimeMillis() < deadline) {
            if (hidDevice != null) {
                if (isAppRegistered) return
                if (!registrationAttempted) {
                    registerApp()
                    registrationAttempted = true
                }
            }
            delay(250)
        }
    }

    private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
    }

    companion object {
        @Volatile
        private var INSTANCE: HidDeviceManager? = null

        fun getInstance(context: Context): HidDeviceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HidDeviceManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // Keyboard (ID 1)
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // USAGE (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // COLLECTION (Application)
            0x85.toByte(), 0x01.toByte(), //   REPORT_ID (1)
            0x05.toByte(), 0x07.toByte(), //   USAGE_PAGE (Keyboard)
            0x19.toByte(), 0xE0.toByte(), //   USAGE_MINIMUM (Keyboard LeftControl)
            0x29.toByte(), 0xE7.toByte(), //   USAGE_MAXIMUM (Keyboard Right GUI)
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
            0x95.toByte(), 0x08.toByte(), //   REPORT_COUNT (8)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
            0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x81.toByte(), 0x03.toByte(), //   INPUT (Cnst,Var,Abs)
            0x95.toByte(), 0x06.toByte(), //   REPORT_COUNT (6)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x65.toByte(), //   LOGICAL_MAXIMUM (101)
            0x05.toByte(), 0x07.toByte(), //   USAGE_PAGE (Keyboard)
            0x19.toByte(), 0x00.toByte(), //   USAGE_MINIMUM (Reserved)
            0x29.toByte(), 0x65.toByte(), //   USAGE_MAXIMUM (Keyboard Application)
            0x81.toByte(), 0x00.toByte(), //   INPUT (Data,Ary,Abs)
            0xC0.toByte(),                // END_COLLECTION

            // Mouse (ID 3)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), 0x03.toByte(), //   Report ID (3)
            0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
            0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), //     Usage Minimum (1)
            0x29.toByte(), 0x03.toByte(), //     Usage Maximum (3)
            0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x75.toByte(), 0x01.toByte(), //     Report Size (1)
            0x81.toByte(), 0x02.toByte(), //     Input (Data,Var,Abs)
            0x95.toByte(), 0x01.toByte(), //     Report Count (1)
            0x75.toByte(), 0x05.toByte(), //     Report Size (5)
            0x81.toByte(), 0x03.toByte(), //     Input (Cnst,Var,Abs)
            0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //     Usage (X)
            0x09.toByte(), 0x31.toByte(), //     Usage (Y)
            0x09.toByte(), 0x38.toByte(), //     Usage (Wheel)
            0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), //     Report Size (8)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x81.toByte(), 0x06.toByte(), //     Input (Data,Var,Rel)
            0xC0.toByte(),                //   End Collection
            0xC0.toByte(),                // End Collection

            // Consumer Control (Media Keys) (ID 4)
            0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
            0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), 0x04.toByte(), //   Report ID (4)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x95.toByte(), 0x07.toByte(), //   Report Count (7)
            0x09.toByte(), 0xB5.toByte(), //   Usage (Scan Next Track)
            0x09.toByte(), 0xB6.toByte(), //   Usage (Scan Previous Track)
            0x09.toByte(), 0xB7.toByte(), //   Usage (Stop)
            0x09.toByte(), 0xCD.toByte(), //   Usage (Play/Pause)
            0x09.toByte(), 0xE2.toByte(), //   Usage (Mute)
            0x09.toByte(), 0xEA.toByte(), //   Usage (Volume Down)
            0x09.toByte(), 0xE9.toByte(), //   Usage (Volume Up)
            0x81.toByte(), 0x02.toByte(), //   Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x81.toByte(), 0x03.toByte(), //   Input (Const,Var,Abs) - Padding
            0xC0.toByte()                 // End Collection
        )
    }
}
