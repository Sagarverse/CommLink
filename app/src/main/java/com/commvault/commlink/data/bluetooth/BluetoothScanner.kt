package com.commvault.commlink.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    } catch (e: Exception) {
        null
    }

    private val _scannedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val scannedDevices: StateFlow<Set<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    // Expose whether Bluetooth is enabled so UI can react
    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled

    private val handler = Handler(Looper.getMainLooper())
    private var scanCycleCount = 0
    private val maxScanCycles = 5  // Auto-restart scanning up to 5 cycles (40 seconds total)

    private val scanStopRunnable = Runnable { onScanCycleEnd() }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                if (state == BluetoothAdapter.STATE_ON) {
                    // Bluetooth just turned on — auto-start scanning if it was requested
                    if (_isScanning.value) {
                        startScanInternal()
                    }
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    stopScanInternal()
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.parcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        _scannedDevices.value = _scannedDevices.value + it
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.device?.let { device ->
                try {
                    _scannedDevices.value = _scannedDevices.value + device
                } catch (e: SecurityException) { }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            val devices = results.map { it.device }
            _scannedDevices.value = _scannedDevices.value + devices
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.w("BluetoothScanner", "LE scan failed with error: $errorCode")
        }
    }

    fun clearDevices() {
        _scannedDevices.value = emptySet()
    }

    /**
     * Enables Bluetooth if it's off, then starts scanning.
     * If BT is off, we request enable and scanning begins once BT comes up.
     */
    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        scanCycleCount = 0

        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.w("BluetoothScanner", "No Bluetooth adapter")
            _isScanning.value = false
            return
        }

        // Auto-enable Bluetooth if it's off
        if (!adapter.isEnabled) {
            Log.d("BluetoothScanner", "Bluetooth off — enabling...")
            _isBluetoothEnabled.value = false
            try {
                // On Android 12+ (S) we can't call enable() directly, use the system intent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(enableIntent)
                } else {
                    @Suppress("DEPRECATION")
                    adapter.enable()
                }
            } catch (e: Exception) {
                Log.e("BluetoothScanner", "Failed to enable Bluetooth", e)
            }
            // Scanning will auto-start via the bluetoothStateReceiver when BT comes on
            return
        }

        startScanInternal()
    }

    private fun startScanInternal() {
        val adapter = bluetoothAdapter ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = context.checkSelfPermission(
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val connectGranted = context.checkSelfPermission(
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!scanGranted || !connectGranted) {
                Log.w("BluetoothScanner", "Missing BT permissions")
                _isScanning.value = false
                return
            }
        }

        if (!adapter.isEnabled) return
        
        // Immediately add bonded devices so they always appear in the list
        try {
            val bonded = adapter.bondedDevices
            if (!bonded.isNullOrEmpty()) {
                _scannedDevices.value = _scannedDevices.value + bonded
            }
        } catch (e: Exception) {}

        // Classic Discovery
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(classicReceiver, filter)
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.w("BluetoothScanner", "Classic discovery failed", e)
        }

        // BLE Scan
        val leScanner = try { adapter.bluetoothLeScanner } catch (e: Exception) { null }
        if (leScanner != null) {
            try {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                leScanner.startScan(null, settings, scanCallback)
            } catch (e: Exception) {
                Log.e("BluetoothScanner", "LE scan start failed", e)
            }
        }

        // Auto-stop this cycle after 8 seconds, then restart if not at max
        handler.removeCallbacks(scanStopRunnable)
        handler.postDelayed(scanStopRunnable, 8000)
    }

    private fun onScanCycleEnd() {
        stopScanInternal()
        scanCycleCount++

        if (scanCycleCount < maxScanCycles && _isScanning.value) {
            // Brief pause then restart to keep Android BT stack happy
            handler.postDelayed({ startScanInternal() }, 500)
        } else {
            _isScanning.value = false
        }
    }

    fun stopScanning() {
        _isScanning.value = false
        stopScanInternal()
        handler.removeCallbacks(scanStopRunnable)
    }

    private fun stopScanInternal() {
        val adapter = bluetoothAdapter ?: return

        try {
            if (adapter.isEnabled && adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            context.unregisterReceiver(classicReceiver)
        } catch (e: Exception) { }

        val leScanner = try { adapter.bluetoothLeScanner } catch (e: Exception) { null }
        try {
            leScanner?.stopScan(scanCallback)
        } catch (e: Exception) { }
    }

    private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
    }
}
