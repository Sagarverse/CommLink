package com.commvault.commlink.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commvault.commlink.data.bluetooth.BluetoothScanner
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.data.repository.DeviceRepositoryImpl
import com.commvault.commlink.data.secure.SecureStorage
import com.commvault.commlink.domain.model.Workstation
import com.commvault.commlink.data.model.AssignedWork
import com.commvault.commlink.data.model.TodoItem
import com.commvault.commlink.data.model.WorkStatus
import com.commvault.commlink.data.model.WorkPriority
import com.commvault.commlink.domain.repository.DeviceRepository
import com.commvault.commlink.data.network.CommLinkNetworkManager
import com.commvault.commlink.data.network.DiscoveredPeer
import com.commvault.commlink.data.model.ChatMessage
import com.commvault.commlink.data.model.MessageStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

class CommLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureStorage(application)
    private val deviceRepository: DeviceRepository = DeviceRepositoryImpl(application)
    private val bluetoothScanner = BluetoothScanner(application)
    private val hidDeviceManager = HidDeviceManager.getInstance(application)
    private val bluetoothAdapter: BluetoothAdapter? = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val networkManager = CommLinkNetworkManager.getInstance(application)

    // Connection Flow
    val connectionMode: StateFlow<HidDeviceManager.ConnectionMode> = hidDeviceManager.connectionMode
    val connectionState: StateFlow<HidDeviceManager.ConnectionState> = hidDeviceManager.connectionState
    val activeModifiers: StateFlow<Byte> = hidDeviceManager.activeModifiers
    val isTextPushing: StateFlow<Boolean> = hidDeviceManager.isTextPushing
    val isPushPaused: StateFlow<Boolean> = hidDeviceManager.isPushPaused

    // Scanner flows
    val isScanning: StateFlow<Boolean> = bluetoothScanner.isScanning
    val scannedDevices: StateFlow<Set<BluetoothDevice>> = bluetoothScanner.scannedDevices

    // Saved & Bonded Workstations
    val savedDevices: StateFlow<List<Workstation>> = combine(
        deviceRepository.knownWorkstations,
        flow {
            while (true) {
                val bonded = try {
                    bluetoothAdapter?.bondedDevices?.map { device ->
                        Workstation(
                            address = device.address,
                            name = device.name ?: "Unknown Device",
                            lastConnected = 0L
                        )
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                emit(bonded)
                delay(5000)
            }
        }
    ) { known, bonded ->
        val merged = (known + bonded).distinctBy { it.address.lowercase() }
        merged.sortedByDescending { it.lastConnected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Login/Credentials Flows
    private val _email = MutableStateFlow(secureStorage.getCommvaultEmail() ?: "")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(secureStorage.isUserLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun login(emailInput: String, passwordInput: String): Boolean {
        if (emailInput.isNotBlank() && passwordInput.isNotBlank() && emailInput.endsWith("@commvault.com", ignoreCase = true)) {
            secureStorage.saveCommvaultEmail(emailInput)
            secureStorage.saveCommvaultPassword(passwordInput)
            _email.value = emailInput
            _isLoggedIn.value = true
            attemptAutoConnect()
            return true
        }
        return false
    }

    private val prefs = application.getSharedPreferences("commlink_settings", Context.MODE_PRIVATE)

    // Settings
    private val _isAutoConnectEnabled = MutableStateFlow(prefs.getBoolean("auto_connect", false))
    val isAutoConnectEnabled: StateFlow<Boolean> = _isAutoConnectEnabled.asStateFlow()

    private val _typingSpeed = MutableStateFlow(prefs.getLong("typing_speed", 120L))
    val typingSpeed: StateFlow<Long> = _typingSpeed.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean("biometric_enabled", false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    // Theme Color
    private val _themeColorHex = MutableStateFlow(secureStorage.getThemeColor())
    val themeColorHex: StateFlow<String> = _themeColorHex.asStateFlow()

    fun setThemeColor(hex: String) {
        _themeColorHex.value = hex
        secureStorage.saveThemeColor(hex)
    }


    // Assigned Works
    private val _assignedWorks = MutableStateFlow<List<AssignedWork>>(emptyList())
    val assignedWorks: StateFlow<List<AssignedWork>> = _assignedWorks.asStateFlow()

    // To-Do List
    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    // Alarms List
    private val _alarmsList = MutableStateFlow<List<com.commvault.commlink.domain.model.AlarmItem>>(emptyList())
    val alarmsList: StateFlow<List<com.commvault.commlink.domain.model.AlarmItem>> = _alarmsList.asStateFlow()

    // P2P Networking & Chat
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = networkManager.discoveredPeers
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        // Apply initial typing speed to HidDeviceManager
        hidDeviceManager.typingDelay = _typingSpeed.value
        loadMockAssignedWorks()
        // Move heavy I/O (encrypted prefs + Gson) off main thread to prevent ANR
        viewModelScope.launch(Dispatchers.IO) {
            loadChatMessages()
        }
    }

    private fun loadMockAssignedWorks() {
        _assignedWorks.value = emptyList()
    }

    fun updateWorkStatus(workId: String, newStatus: WorkStatus) {
        _assignedWorks.value = _assignedWorks.value.map {
            if (it.id == workId) it.copy(status = newStatus) else it
        }
    }

    // Todo functions
    fun addTodo(title: String, description: String = "") {
        val newTodo = TodoItem(title = title, description = description)
        _todos.value = _todos.value + newTodo
        saveTodos()
    }

    fun toggleTodo(todoId: String) {
        _todos.value = _todos.value.map {
            if (it.id == todoId) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveTodos()
    }

    fun deleteTodo(todoId: String) {
        _todos.value = _todos.value.filter { it.id != todoId }
        saveTodos()
    }

    private fun loadTodos() {
        try {
            val json = secureStorage.getTodosJson()
            if (json.isNotBlank() && json != "[]") {
                val listType = object : com.google.gson.reflect.TypeToken<List<TodoItem>>() {}.type
                val loadedList: List<TodoItem> = com.google.gson.Gson().fromJson(json, listType)
                _todos.value = loadedList
            }
        } catch (e: Exception) {
            android.util.Log.e("CommLinkViewModel", "Error loading todos", e)
        }
    }

    private fun saveTodos() {
        try {
            val json = com.google.gson.Gson().toJson(_todos.value)
            secureStorage.saveTodosJson(json)
        } catch (e: Exception) {
            android.util.Log.e("CommLinkViewModel", "Error saving todos", e)
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_connect", enabled).apply()
        _isAutoConnectEnabled.value = enabled
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _isBiometricEnabled.value = enabled
    }

    fun setTypingSpeed(speedMs: Long) {
        prefs.edit().putLong("typing_speed", speedMs).apply()
        _typingSpeed.value = speedMs
        hidDeviceManager.typingDelay = speedMs
    }

    fun setConnectionMode(mode: HidDeviceManager.ConnectionMode) {
        hidDeviceManager.setConnectionMode(mode)
    }

    // Keep Alive Status
    private val _isKeepAliveActive = MutableStateFlow(false)
    val isKeepAliveActive: StateFlow<Boolean> = _isKeepAliveActive
    
    private val _trackpadSensitivity = MutableStateFlow(1.8f)
    val trackpadSensitivity: StateFlow<Float> = _trackpadSensitivity

    private var keepAliveJob: Job? = null

    // DuckyScript Interpreter Running State
    private val _isMacroRunning = MutableStateFlow(false)
    val isMacroRunning: StateFlow<Boolean> = _isMacroRunning.asStateFlow()

    private val _isMacroPaused = MutableStateFlow(false)
    val isMacroPaused: StateFlow<Boolean> = _isMacroPaused.asStateFlow()

    private val _isPresentationModeActive = MutableStateFlow(false)
    val isPresentationModeActive: StateFlow<Boolean> = _isPresentationModeActive

    fun setPresentationModeActive(active: Boolean) {
        _isPresentationModeActive.value = active
    }

    private val _macroPrompt = MutableStateFlow<String?>(null)
    val macroPrompt: StateFlow<String?> = _macroPrompt.asStateFlow()
    private var macroPromptDeferred: kotlinx.coroutines.CompletableDeferred<String>? = null

    private var macroJob: Job? = null

    // Snippets list
    private val _snippets = MutableStateFlow<List<TextSnippet>>(emptyList())
    val snippets: StateFlow<List<TextSnippet>> = _snippets.asStateFlow()

    // Password vault list
    private val _passwordEntries = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val passwordEntries: StateFlow<List<PasswordEntry>> = _passwordEntries.asStateFlow()

    init {
        // Move heavy I/O (encrypted prefs + Gson) off main thread to prevent ANR
        viewModelScope.launch(Dispatchers.IO) {
            loadSnippets()
            loadPasswordEntries()
            loadAlarms()
            loadTodos()
        }
        setupNetworkListener()
        if (_isLoggedIn.value) {
            attemptAutoConnect()
            startNetworkDiscovery()
        }
    }

    private fun setupNetworkListener() {
        networkManager.onMessageReceived = { payload ->
            viewModelScope.launch {
                val type = payload.optString("type")
                when (type) {
                    "CHAT_MESSAGE" -> handleIncomingChatMessage(payload)
                    "MESSAGE_ACK" -> handleMessageAck(payload)
                    "HELP_PING" -> handleIncomingPing(payload)
                    "EMERGENCY_PING" -> handleEmergencyPing(payload)
                    "ASSIGN_WORK" -> handleIncomingWork(payload)
                    "FILE_OFFER" -> handleIncomingFileOffer(payload)
                }
            }
        }
    }

    fun getCleanUserName(): String {
        val raw = _email.value.substringBefore("@").uppercase()
        val knownNames = listOf("HIMATHI", "KARAN", "MANOJ", "MONIKA", "NEHA", "SAGAR", "YASH")
        for (name in knownNames) {
            if (raw.contains(name)) {
                return name
            }
        }
        return raw.replace(".", " ").replace("_", " ").trim()
    }

    private fun startNetworkDiscovery() {
        val userName = getCleanUserName()
        if (userName.isNotBlank()) {
            networkManager.startNetworking(userName)
        }
    }

    private var watchdogJob: Job? = null

    fun attemptAutoConnect() {
        if (watchdogJob?.isActive == true) return
        
        watchdogJob = viewModelScope.launch(Dispatchers.IO) {
            var delayMs = 5000L
            // Give system time to initialize BT stack on startup
            delay(2000)
            
            while (isActive) {
                if (_isAutoConnectEnabled.value && connectionState.value is HidDeviceManager.ConnectionState.Disconnected && hidDeviceManager.connectionMode.value == HidDeviceManager.ConnectionMode.BLUETOOTH) {
                    val devices = savedDevices.value
                    if (devices.isNotEmpty()) {
                        val lastDevice = devices.first() // List is sorted by lastConnected descending
                        connectDevice(lastDevice.address)
                    }
                }
                
                // Reset backoff if connected or auto connect is disabled
                if (!_isAutoConnectEnabled.value || connectionState.value !is HidDeviceManager.ConnectionState.Disconnected) {
                    delayMs = 5000L
                    delay(delayMs)
                } else {
                    // Wait with exponential backoff if disconnected
                    delay(delayMs)
                    delayMs = (delayMs * 1.5).toLong().coerceAtMost(60000L) // Max out at 1 minute between attempts
                }
            }
        }
    }

    // Snippets Management
    fun loadSnippets() {
        val json = prefs.getString("text_snippets", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<TextSnippet>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TextSnippet(
                        name = o.getString("name"),
                        content = o.getString("content"),
                        category = o.optString("category", "General")
                    )
                )
            }
            _snippets.value = list
        } catch (e: Exception) {
            _snippets.value = emptyList()
        }
    }

    fun saveSnippet(name: String, content: String, category: String) {
        val current = _snippets.value.toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        current.add(TextSnippet(name, content, category))
        persistSnippets(current)
    }

    fun deleteSnippet(name: String) {
        val current = _snippets.value.filter { !it.name.equals(name, ignoreCase = true) }
        persistSnippets(current)
    }

    private fun persistSnippets(list: List<TextSnippet>) {
        _snippets.value = list
        val arr = org.json.JSONArray()
        list.forEach { s ->
            arr.put(org.json.JSONObject().apply {
                put("name", s.name)
                put("content", s.content)
                put("category", s.category)
            })
        }
        prefs.edit().putString("text_snippets", arr.toString()).apply()
    }

    // Keep Alive Loop (Sends harmless SHIFT click to keep laptop active)
    fun toggleKeepAlive() {
        if (_isKeepAliveActive.value) {
            stopKeepAlive()
        } else {
            startKeepAlive()
        }
    }

    private fun startKeepAlive() {
        stopKeepAlive()
        _isKeepAliveActive.value = true
        keepAliveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _isKeepAliveActive.value) {
                // Send a microscopic mouse jiggle to keep PC awake
                sendMouseMove(1f, 1f)
                delay(50L)
                sendMouseMove(-1f, -1f)
                delay(50000L)
            }
        }
    }

    fun setTrackpadSensitivity(value: Float) {
        _trackpadSensitivity.value = value
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        _isKeepAliveActive.value = false
    }

    // Safe DuckyScript interpreter for office tools macros
    fun runMacroScript(script: String) {
        if (_isMacroRunning.value) return
        _isMacroRunning.value = true
        _isMacroPaused.value = false
        macroJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val lines = script.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("REM") }
                var defaultDelay = 100L
                for (line in lines) {
                    if (!isActive) break
                    
                    while (_isMacroPaused.value) {
                        delay(100)
                    }

                    val parts = line.split(" ", limit = 2)
                    val cmd = parts[0].uppercase()
                    val arg = if (parts.size > 1) parts[1] else ""

                    when (cmd) {
                        "DEFAULTDELAY" -> defaultDelay = arg.toLongOrNull() ?: defaultDelay
                        "DELAY" -> delay(arg.toLongOrNull() ?: defaultDelay)
                        "STRING" -> hidDeviceManager.sendText(arg)?.join()
                        "ENTER" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_ENTER)
                        "TAB" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_TAB)
                        "SPACE" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_SPACE)
                        "ESCAPE", "ESC" -> sendKey(0x29.toByte())
                        "BACKSPACE" -> sendKey(0x2A.toByte())
                        "DELETE", "DEL" -> sendKey(0x4C.toByte())
                        "CAPSLOCK" -> sendKey(0x39.toByte())
                        "PRINTSCREEN" -> sendKey(0x46.toByte())
                        "SCROLLLOCK" -> sendKey(0x47.toByte())
                        "PAUSE", "BREAK" -> sendKey(0x48.toByte())
                        "INSERT" -> sendKey(0x49.toByte())
                        "HOME" -> sendKey(0x4A.toByte())
                        "END" -> sendKey(0x4D.toByte())
                        "PAGEUP" -> sendKey(0x4B.toByte())
                        "PAGEDOWN" -> sendKey(0x4E.toByte())
                        "UP", "UPARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_UP)
                        "DOWN", "DOWNARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_DOWN)
                        "LEFT", "LEFTARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_LEFT)
                        "RIGHT", "RIGHTARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_RIGHT)
                        "F1" -> sendKey(0x3A.toByte())
                        "F2" -> sendKey(0x3B.toByte())
                        "F3" -> sendKey(0x3C.toByte())
                        "F4" -> sendKey(0x3D.toByte())
                        "F5" -> sendKey(0x3E.toByte())
                        "F6" -> sendKey(0x3F.toByte())
                        "F7" -> sendKey(0x40.toByte())
                        "F8" -> sendKey(0x41.toByte())
                        "F9" -> sendKey(0x42.toByte())
                        "F10" -> sendKey(0x43.toByte())
                        "F11" -> sendKey(0x44.toByte())
                        "F12" -> sendKey(0x45.toByte())
                        "GUI", "WINDOWS", "WIN" -> {
                            val key = parseDuckyKey(arg)
                            sendKey(key, com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_GUI)
                        }
                        "CTRL", "CONTROL" -> {
                            val key = parseDuckyKey(arg)
                            sendKey(key, com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_CTRL)
                        }
                        "ALT" -> {
                            val key = parseDuckyKey(arg)
                            sendKey(key, com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_ALT)
                        }
                        "SHIFT" -> {
                            val key = parseDuckyKey(arg)
                            sendKey(key, com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_SHIFT)
                        }
                        "MEDIA" -> {
                            // Consumer control media keys via HID consumer report
                            when (arg.uppercase()) {
                                "PLAYPAUSE" -> hidDeviceManager.sendConsumerKey(0xCD.toShort())
                                "NEXT" -> hidDeviceManager.sendConsumerKey(0xB5.toShort())
                                "PREV" -> hidDeviceManager.sendConsumerKey(0xB6.toShort())
                                "VOL_UP" -> hidDeviceManager.sendConsumerKey(0xE9.toShort())
                                "VOL_DOWN" -> hidDeviceManager.sendConsumerKey(0xEA.toShort())
                                "MUTE" -> hidDeviceManager.sendConsumerKey(0xE2.toShort())
                            }
                        }
                        "MOUSE_CLICK" -> {
                            val btnStr = arg.trim().uppercase()
                            val btn = when (btnStr) {
                                "LEFT", "1" -> 1
                                "RIGHT", "2" -> 2
                                "MIDDLE", "4" -> 4
                                else -> 1
                            }
                            sendMouseMove(0f, 0f, buttons = btn)
                            delay(40)
                            sendMouseMove(0f, 0f, buttons = 0)
                        }
                        "MOUSE_MOVE" -> {
                            val coords = arg.split(",").map { it.trim().toFloatOrNull() ?: 0f }
                            if (coords.size >= 2) sendMouseMove(coords[0], coords[1])
                        }
                        "MOUSE_SCROLL" -> {
                            val amount = arg.toIntOrNull() ?: 0
                            sendMouseMove(0f, 0f, wheel = amount)
                        }
                        "ASK_INPUT" -> {
                            val deferred = kotlinx.coroutines.CompletableDeferred<String>()
                            macroPromptDeferred = deferred
                            _macroPrompt.value = arg.ifBlank { "Enter value:" }
                            val userInput = deferred.await()
                            _macroPrompt.value = null
                            macroPromptDeferred = null
                            if (userInput.isNotEmpty()) {
                                hidDeviceManager.sendText(userInput)?.join()
                            }
                        }
                    }
                    delay(10L)
                }
            } finally {
                _isMacroRunning.value = false
                _isMacroPaused.value = false
            }
        }
    }

    fun pauseMacro() {
        if (_isMacroRunning.value) {
            _isMacroPaused.value = true
        }
    }

    fun resumeMacro() {
        if (_isMacroRunning.value) {
            _isMacroPaused.value = false
        }
    }

    fun stopMacro() {
        macroJob?.cancel()
        macroJob = null
        _isMacroRunning.value = false
        _isMacroPaused.value = false
        _macroPrompt.value = null
        macroPromptDeferred?.cancel()
        macroPromptDeferred = null
    }

    fun submitMacroPrompt(input: String) {
        macroPromptDeferred?.complete(input)
    }

    fun cancelMacroPrompt() {
        stopMacro()
    }

    private fun parseDuckyKey(arg: String): Byte {
        val uArg = arg.uppercase()
        return when (uArg) {
            "SPACE" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_SPACE
            "ENTER" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_ENTER
            "TAB" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_TAB
            "ESC", "ESCAPE" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_ESC
            "BACKSPACE", "DELETE", "DEL" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_BACKSPACE
            "LEFT" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_LEFT
            "RIGHT" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_RIGHT
            "UP" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_UP
            "DOWN" -> com.commvault.commlink.domain.model.HidKeyCodes.KEY_DOWN
            else -> {
                if (uArg.length == 1 && uArg[0] in 'A'..'Z') {
                    (com.commvault.commlink.domain.model.HidKeyCodes.KEY_A + (uArg[0] - 'A')).toByte()
                } else if (uArg.length == 1 && uArg[0] in '0'..'9') {
                    if (uArg[0] == '0') com.commvault.commlink.domain.model.HidKeyCodes.KEY_0 else (com.commvault.commlink.domain.model.HidKeyCodes.KEY_1 + (uArg[0] - '1')).toByte()
                } else com.commvault.commlink.domain.model.HidKeyCodes.KEY_NONE
            }
        }
    }

    override fun onCleared() {
        stopKeepAlive()
        restoreOriginalMac()
        stopAutoLockMonitor()
        super.onCleared()
    }

    fun logout() {
        stopKeepAlive()
        networkManager.stopNetworking()
        secureStorage.clearCredentials()
        _email.value = ""
        _isLoggedIn.value = false
        disconnect()
    }

    fun getSavedPassword(): String {
        return secureStorage.getCommvaultPassword() ?: ""
    }

    // Scanning controller
    fun startScanning() {
        bluetoothScanner.clearDevices()
        bluetoothScanner.startScanning()
    }

    fun stopScanning() {
        bluetoothScanner.stopScanning()
    }

    // HID actions
    fun connect(device: BluetoothDevice) {
        hidDeviceManager.connectWithRetry(device)
    }

    fun connectDevice(address: String) {
        val adapter = bluetoothAdapter
        if (adapter != null) {
            try {
                val remoteDevice = adapter.getRemoteDevice(address)
                connect(remoteDevice)
            } catch (e: Exception) {
                Log.e("CommLinkViewModel", "Failed to resolve device by address: $address", e)
            }
        }
    }

    fun disconnect() {
        stopKeepAlive()
        hidDeviceManager.disconnect()
    }

    fun sendKey(keyCode: Byte, modifier: Byte = 0) {
        hidDeviceManager.sendKeyPress(keyCode, modifier)
    }

    fun sendMediaPlayPause() = hidDeviceManager.sendMediaKey(0x08) // Bit 3
    fun sendMediaNext() = hidDeviceManager.sendMediaKey(0x01) // Bit 0
    fun sendMediaPrev() = hidDeviceManager.sendMediaKey(0x02) // Bit 1
    fun sendMediaVolumeUp() = hidDeviceManager.sendMediaKey(0x40) // Bit 6
    fun sendMediaVolumeDown() = hidDeviceManager.sendMediaKey(0x20) // Bit 5
    fun sendMediaMute() = hidDeviceManager.sendMediaKey(0x10) // Bit 4

    // Presentation mode helpers
    fun sendPresentationNext() = hidDeviceManager.sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_RIGHT, useSticky = false)
    fun sendPresentationPrev() = hidDeviceManager.sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_LEFT, useSticky = false)
    fun sendPresentationStart() = hidDeviceManager.sendKeyPress(0x3E.toByte(), useSticky = false) // F5
    fun sendPresentationEnd() = hidDeviceManager.sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_ESC, useSticky = false)
    fun sendPresentationBlack() = hidDeviceManager.sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_B, useSticky = false)
    fun sendPresentationWhite() = hidDeviceManager.sendKeyPress(com.commvault.commlink.domain.model.HidKeyCodes.KEY_W, useSticky = false)

    fun toggleModifier(modifier: Byte) {
        val current = hidDeviceManager.activeModifiers.value
        val isSet = (current.toInt() and modifier.toInt()) != 0
        hidDeviceManager.setModifier(modifier, !isSet)
    }

    fun sendText(text: String) {
        hidDeviceManager.sendText(text)
    }
    
    fun stopTextPush() {
        hidDeviceManager.stopTextPush()
    }
    
    fun toggleTextPushPause() {
        hidDeviceManager.toggleTextPushPause()
    }

    fun sendMouseMove(dx: Float, dy: Float, buttons: Int = 0, wheel: Int = 0) {
        hidDeviceManager.sendMouseMove(dx, dy, buttons, wheel)
    }

    fun setMouseLocked(locked: Boolean) {
        hidDeviceManager.isMouseLocked = locked
        if (!locked) {
            hidDeviceManager.resetMouseAccumulator()
        }
    }

    fun lockWindows() {
        hidDeviceManager.lockWindows()
    }

    fun unlockWindows(wakeScreenFirst: Boolean = true) {
        val password = secureStorage.getCommvaultPassword() ?: ""
        if (password.isNotEmpty()) {
            hidDeviceManager.unlockWindows(password, wakeScreenFirst)
        }
    }

    // Password Vault Management
    fun loadPasswordEntries() {
        val json = secureStorage.getPasswordVaultJson()
        try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<PasswordEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PasswordEntry(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        username = o.optString("username", null),
                        value = o.getString("value"),
                        category = o.optString("category", "General")
                    )
                )
            }
            _passwordEntries.value = list
        } catch (e: Exception) {
            _passwordEntries.value = emptyList()
        }
    }

    fun savePasswordEntry(name: String, username: String?, value: String, category: String) {
        val current = _passwordEntries.value.toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        val id = java.util.UUID.randomUUID().toString()
        current.add(PasswordEntry(id, name, username, value, category))
        persistPasswordEntries(current)
    }

    fun deletePasswordEntry(id: String) {
        val current = _passwordEntries.value.filter { it.id != id }
        persistPasswordEntries(current)
    }

    private fun persistPasswordEntries(list: List<PasswordEntry>) {
        _passwordEntries.value = list
        val arr = org.json.JSONArray()
        list.forEach { p ->
            arr.put(org.json.JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                if (p.username != null) put("username", p.username)
                put("value", p.value)
                put("category", p.category)
            })
        }
        secureStorage.savePasswordVaultJson(arr.toString())
    }

    fun typeVaultCredential(
        entry: PasswordEntry,
        activity: androidx.fragment.app.FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authenticator = com.commvault.commlink.data.secure.BiometricAuthenticator(activity)
        if (!authenticator.isBiometricAvailable()) {
            // Fallback for emulators/devices without hardware: Type directly
            sendText(entry.value)
            onSuccess()
            return
        }

        authenticator.authenticate(
            title = "Confirm Identity",
            subtitle = "Authorize typing password for ${entry.name}",
            onSuccess = {
                sendText(entry.value)
                onSuccess()
            },
            onError = { err ->
                onError(err)
            }
        )
    }

    // Chat Persistence (24 Hour TTL)
    private fun loadChatMessages() {
        val json = prefs.getString("chat_messages", "[]") ?: "[]"
        try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val messages: List<ChatMessage> = Gson().fromJson(json, type) ?: emptyList()
            
            // Filter strictly for TTL 24 Hours
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursMs = 24L * 60L * 60L * 1000L
            val validMessages = messages.filter { currentTime - it.timestamp <= twentyFourHoursMs }
            
            _chatMessages.value = validMessages
            
            if (validMessages.size != messages.size) {
                // If we dropped messages, save the new cleaned list immediately
                saveChatMessages()
            }
        } catch (e: Exception) {
            android.util.Log.e("CommLinkViewModel", "Failed to load chat messages", e)
            _chatMessages.value = emptyList()
        }
    }

    private fun saveChatMessages() {
        val json = Gson().toJson(_chatMessages.value)
        prefs.edit().putString("chat_messages", json).apply()
    }

    // Networking Methods
    fun sendChatMessage(targetName: String, text: String) {
        val payload = JSONObject().apply {
            put("type", "CHAT_MESSAGE")
            put("senderName", getCleanUserName())
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }

        val msgId = java.util.UUID.randomUUID().toString()
        val msg = ChatMessage(
            id = msgId,
            senderName = getCleanUserName(),
            receiverName = targetName,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isFromMe = true,
            messageType = com.commvault.commlink.data.model.MessageType.TEXT
        )
        
        _chatMessages.value = _chatMessages.value + msg

        networkManager.sendMessage(targetName, payload,
            onSuccess = {
                val updated = msg.copy(status = MessageStatus.SENT)
                _chatMessages.value = _chatMessages.value.map { if (it.id == msgId) updated else it }
            },
            onError = {
                val updated = msg.copy(status = MessageStatus.FAILED)
                _chatMessages.value = _chatMessages.value.map { if (it.id == msgId) updated else it }
            }
        )
    }

    fun sendFileMessage(targetName: String, uri: android.net.Uri, fileName: String, fileSize: Long, context: Context) {
        val msgId = java.util.UUID.randomUUID().toString()
        val msg = ChatMessage(
            id = msgId,
            senderName = getCleanUserName(),
            receiverName = targetName,
            text = "Sending file: $fileName",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isFromMe = true,
            messageType = com.commvault.commlink.data.model.MessageType.FILE,
            fileName = fileName,
            fileSize = fileSize,
            fileUri = uri.toString()
        )
        _chatMessages.value = _chatMessages.value + msg

        networkManager.sendFile(
            senderName = getCleanUserName(),
            targetName = targetName,
            fileName = fileName,
            fileSize = fileSize,
            fileUri = uri,
            onSuccess = {
                val updated = msg.copy(status = MessageStatus.SENT)
                _chatMessages.value = _chatMessages.value.map { if (it.id == msgId) updated else it }
            },
            onError = { error ->
                val updated = msg.copy(status = MessageStatus.FAILED, text = "Failed to send: $error")
                _chatMessages.value = _chatMessages.value.map { if (it.id == msgId) updated else it }
            }
        )
    }

    private fun handleIncomingFileOffer(payload: JSONObject) {
        val senderIp = payload.optString("senderIp", "")
        val senderName = payload.optString("senderName", "Unknown")
        val fileName = payload.optString("fileName", "file")
        val fileSize = payload.optLong("fileSize", 0)
        val port = payload.optInt("port", 0)
        
        if (senderIp.isEmpty() || port == 0) return
        
        val msgId = java.util.UUID.randomUUID().toString()
        val incomingMsg = ChatMessage(
            id = msgId,
            senderName = senderName,
            receiverName = getCleanUserName(),
            text = "Receiving file...",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isFromMe = false,
            messageType = com.commvault.commlink.data.model.MessageType.FILE,
            fileName = fileName,
            fileSize = fileSize
        )
        
        _chatMessages.value = _chatMessages.value + incomingMsg
        
        networkManager.downloadFile(senderIp, port, fileName,
            onSuccess = { path ->
                val updatedMsg = incomingMsg.copy(
                    text = "File received",
                    status = MessageStatus.DELIVERED,
                    fileUri = path
                )
                _chatMessages.value = _chatMessages.value.map { if (it.id == msgId) updatedMsg else it }
            },
            onError = { error ->
                val updatedMsg = incomingMsg.copy(
                    text = "Failed to receive file: $error",
                    status = MessageStatus.FAILED
                )
                _chatMessages.value = _chatMessages.value.map { if (it.id == msgId) updatedMsg else it }
            }
        )
    }

    private fun updateMessageStatus(id: String, status: MessageStatus) {
        _chatMessages.value = _chatMessages.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
        saveChatMessages()
    }

    private fun handleIncomingChatMessage(payload: JSONObject) {
        val id = payload.getString("id")
        val sender = payload.getString("senderName")
        val text = payload.getString("text")
        val myName = getCleanUserName()

        val msg = ChatMessage(id, sender, myName, text, System.currentTimeMillis(), MessageStatus.DELIVERED, false)
        _chatMessages.value = _chatMessages.value + msg
        saveChatMessages()

        // Send ACK back
        val ackPayload = JSONObject().apply {
            put("type", "MESSAGE_ACK")
            put("id", id)
        }
        networkManager.sendMessage(sender, ackPayload, {}, {})
    }

    private fun handleMessageAck(payload: JSONObject) {
        val id = payload.getString("id")
        updateMessageStatus(id, MessageStatus.DELIVERED)
    }

    fun sendHelpPing(targetName: String, context: Context) {
        val myName = getCleanUserName()
        val payload = JSONObject().apply {
            put("type", "HELP_PING")
            put("senderName", myName)
        }
        networkManager.sendMessage(targetName, payload,
            onSuccess = {
                android.widget.Toast.makeText(context, "Ping sent to $targetName", android.widget.Toast.LENGTH_SHORT).show()
            },
            onError = {
                android.widget.Toast.makeText(context, "$targetName is offline on local network.", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleIncomingPing(payload: JSONObject) {
        val sender = payload.getString("senderName")
        val context = getApplication<Application>().applicationContext
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "help_ping_channel",
                "Help Pings",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, "help_ping_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Urgent Help Needed!")
            .setContentText("$sender has requested your assistance via P2P Ping.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(sender.hashCode(), notification)
    }

    fun sendAssignedWork(targetName: String, work: AssignedWork, context: Context, onSuccess: () -> Unit) {
        val myName = getCleanUserName()
        val payload = JSONObject().apply {
            put("type", "ASSIGN_WORK")
            put("id", work.id)
            put("title", work.title)
            put("description", work.description)
            put("priority", work.priority.name)
            put("dueDate", work.dueDate)
            put("senderName", myName)
        }
        networkManager.sendMessage(targetName, payload,
            onSuccess = {
                android.widget.Toast.makeText(context, "Work assigned to $targetName", android.widget.Toast.LENGTH_SHORT).show()
                onSuccess()
            },
            onError = {
                android.widget.Toast.makeText(context, "Failed: $targetName is offline.", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleIncomingWork(payload: JSONObject) {
        val sender = payload.getString("senderName")
        val work = AssignedWork(
            id = payload.getString("id"),
            title = payload.getString("title"),
            description = payload.getString("description"),
            managerName = sender,
            dueDate = payload.getString("dueDate"),
            priority = WorkPriority.valueOf(payload.getString("priority")),
            status = WorkStatus.PENDING
        )
        _assignedWorks.value = _assignedWorks.value + work
        
        val context = getApplication<Application>().applicationContext
        android.widget.Toast.makeText(context, "New work assigned by $sender!", android.widget.Toast.LENGTH_LONG).show()
    }
    fun sendEmergencyPing(targetName: String, context: Context) {
        val myName = getCleanUserName()
        val payload = JSONObject().apply {
            put("type", "EMERGENCY_PING")
            put("senderName", myName)
        }
        networkManager.sendMessage(targetName, payload,
            onSuccess = {
                android.widget.Toast.makeText(context, "EMERGENCY ALARM triggered on $targetName's device!", android.widget.Toast.LENGTH_LONG).show()
            },
            onError = {
                android.widget.Toast.makeText(context, "Failed: $targetName is offline.", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private var emergencyMediaPlayer: android.media.MediaPlayer? = null

    private fun handleEmergencyPing(payload: JSONObject) {
        val sender = payload.getString("senderName")
        val context = getApplication<Application>().applicationContext
        
        // Show High Priority Notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "emergency_ping_channel",
                "Emergency Overrides",
                android.app.NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Critical On-Call Emergency Override"
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, "emergency_ping_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 EMERGENCY OVERRIDE")
            .setContentText("$sender has triggered a critical on-call alarm!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(null, true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify("EMERGENCY".hashCode(), notification)

        // Force Audio Playback on ALARM stream (bypasses silent/mute)
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                
                val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM) 
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)

                emergencyMediaPlayer?.release()
                emergencyMediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }

                // Stop after 15 seconds
                delay(15000L)
                emergencyMediaPlayer?.stop()
                emergencyMediaPlayer?.release()
                emergencyMediaPlayer = null

            } catch (e: Exception) {
                android.util.Log.e("EmergencyOverride", "Failed to play alarm", e)
            }
        }
    }

    // --- ALARM FEATURES ---
    private var intervalAlarmJob: Job? = null
    private var escalatingAlarmJob: Job? = null
    private var alarmMediaPlayer: android.media.MediaPlayer? = null

    fun stopAlarms() {
        intervalAlarmJob?.cancel()
        escalatingAlarmJob?.cancel()
        alarmMediaPlayer?.stop()
        alarmMediaPlayer?.release()
        alarmMediaPlayer = null
        
        // Stop any background scheduled alarm sounds
        com.commvault.commlink.receiver.AlarmSoundPlayer.stop()
        try {
            val notificationManager = com.commvault.commlink.CommLinkApp.instance.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(1001)
        } catch(e: Exception) {}
    }

    fun scheduleExactAlarm(
        timeInMillis: Long, 
        title: String, 
        message: String, 
        alarmType: String,
        maxVolume: Int,
        intervalSecs: Int,
        repeatCount: Int,
        context: Context
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.commvault.commlink.receiver.AlarmReceiver::class.java).apply {
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_MESSAGE", message)
            putExtra("ALARM_TYPE", alarmType)
            putExtra("MAX_VOLUME", maxVolume)
            putExtra("INTERVAL_SECS", intervalSecs)
            putExtra("REPEAT_COUNT", repeatCount)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            timeInMillis.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    // Fallback or request permission
                    val intentSettings = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    context.startActivity(intentSettings)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("CommLinkViewModel", "Permission denied for exact alarm", e)
        }
    }

    private fun loadAlarms() {
        try {
            val json = secureStorage.getAlarmsJson()
            if (json.isNotBlank() && json != "[]") {
                val listType = object : com.google.gson.reflect.TypeToken<List<com.commvault.commlink.domain.model.AlarmItem>>() {}.type
                val loadedList: List<com.commvault.commlink.domain.model.AlarmItem> = com.google.gson.Gson().fromJson(json, listType)
                _alarmsList.value = loadedList
            }
        } catch (e: Exception) {
            android.util.Log.e("CommLinkViewModel", "Error loading alarms", e)
        }
    }

    fun saveAlarm(alarm: com.commvault.commlink.domain.model.AlarmItem, context: Context) {
        val currentList = _alarmsList.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == alarm.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = alarm
        } else {
            currentList.add(alarm)
        }
        
        // Sort by time
        currentList.sortBy { it.timeInMillis }
        
        _alarmsList.value = currentList
        val json = com.google.gson.Gson().toJson(currentList)
        secureStorage.saveAlarmsJson(json)
        
        if (alarm.isEnabled && alarm.timeInMillis > System.currentTimeMillis()) {
            scheduleExactAlarm(
                timeInMillis = alarm.timeInMillis,
                title = alarm.title,
                message = "Scheduled Alarm Triggered",
                alarmType = alarm.alarmType,
                maxVolume = alarm.maxVolume,
                intervalSecs = alarm.intervalSecs,
                repeatCount = alarm.repeatCount,
                context = context
            )
        } else if (!alarm.isEnabled) {
            cancelOSAlarm(alarm.timeInMillis, context)
        }
    }

    fun deleteAlarm(alarmId: String, context: Context) {
        val currentList = _alarmsList.value.toMutableList()
        val alarmToDelete = currentList.find { it.id == alarmId }
        if (alarmToDelete != null) {
            cancelOSAlarm(alarmToDelete.timeInMillis, context)
            currentList.remove(alarmToDelete)
            _alarmsList.value = currentList
            val json = com.google.gson.Gson().toJson(currentList)
            secureStorage.saveAlarmsJson(json)
        }
    }

    fun toggleAlarm(alarmId: String, isEnabled: Boolean, context: Context) {
        val currentList = _alarmsList.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == alarmId }
        if (existingIndex >= 0) {
            val updatedAlarm = currentList[existingIndex].copy(isEnabled = isEnabled)
            currentList[existingIndex] = updatedAlarm
            _alarmsList.value = currentList
            val json = com.google.gson.Gson().toJson(currentList)
            secureStorage.saveAlarmsJson(json)
            
            if (isEnabled && updatedAlarm.timeInMillis > System.currentTimeMillis()) {
                scheduleExactAlarm(
                    timeInMillis = updatedAlarm.timeInMillis,
                    title = updatedAlarm.title,
                    message = "Scheduled Alarm Triggered",
                    alarmType = updatedAlarm.alarmType,
                    maxVolume = updatedAlarm.maxVolume,
                    intervalSecs = updatedAlarm.intervalSecs,
                    repeatCount = updatedAlarm.repeatCount,
                    context = context
                )
            } else {
                cancelOSAlarm(updatedAlarm.timeInMillis, context)
            }
        }
    }

    private fun cancelOSAlarm(timeInMillis: Long, context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.commvault.commlink.receiver.AlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            timeInMillis.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // --- HYDRATION REMINDERS ---
    private val _hydrationReminders = MutableStateFlow<List<com.commvault.commlink.domain.model.HydrationReminder>>(emptyList())
    val hydrationReminders: StateFlow<List<com.commvault.commlink.domain.model.HydrationReminder>> = _hydrationReminders.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadHydrationReminders()
        }
    }

    private fun loadHydrationReminders() {
        try {
            val json = secureStorage.getHydrationRemindersJson()
            if (json.isNotBlank() && json != "[]") {
                val listType = object : com.google.gson.reflect.TypeToken<List<com.commvault.commlink.domain.model.HydrationReminder>>() {}.type
                val loadedList: List<com.commvault.commlink.domain.model.HydrationReminder> = com.google.gson.Gson().fromJson(json, listType)
                _hydrationReminders.value = loadedList
            }
        } catch (e: Exception) {
            android.util.Log.e("CommLinkViewModel", "Error loading hydration reminders", e)
        }
    }

    private fun persistHydrationReminders() {
        val json = com.google.gson.Gson().toJson(_hydrationReminders.value)
        secureStorage.saveHydrationRemindersJson(json)
    }

    fun saveHydrationReminder(reminder: com.commvault.commlink.domain.model.HydrationReminder, context: Context) {
        val currentList = _hydrationReminders.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == reminder.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = reminder
        } else {
            currentList.add(reminder)
        }
        currentList.sortBy { it.hour * 60 + it.minute }
        _hydrationReminders.value = currentList
        persistHydrationReminders()

        if (reminder.isEnabled) {
            scheduleHydrationReminder(reminder, context)
        } else {
            cancelHydrationReminder(reminder, context)
        }
    }

    fun deleteHydrationReminder(reminderId: String, context: Context) {
        val reminder = _hydrationReminders.value.find { it.id == reminderId }
        if (reminder != null) {
            cancelHydrationReminder(reminder, context)
            _hydrationReminders.value = _hydrationReminders.value.filter { it.id != reminderId }
            persistHydrationReminders()
        }
    }

    fun toggleHydrationReminder(reminderId: String, isEnabled: Boolean, context: Context) {
        val currentList = _hydrationReminders.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == reminderId }
        if (existingIndex >= 0) {
            val updated = currentList[existingIndex].copy(isEnabled = isEnabled)
            currentList[existingIndex] = updated
            _hydrationReminders.value = currentList
            persistHydrationReminders()

            if (isEnabled) {
                scheduleHydrationReminder(updated, context)
            } else {
                cancelHydrationReminder(updated, context)
            }
        }
    }

    private fun scheduleHydrationReminder(reminder: com.commvault.commlink.domain.model.HydrationReminder, context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.commvault.commlink.receiver.HydrationReminderReceiver::class.java).apply {
            putExtra("HYDRATION_LABEL", reminder.label)
            putExtra("HYDRATION_REMINDER_ID", reminder.id)
            putExtra("HYDRATION_HOUR", reminder.hour)
            putExtra("HYDRATION_MINUTE", reminder.minute)
        }
        val requestCode = reminder.id.hashCode() + 5000
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate next trigger time
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
            set(java.util.Calendar.MINUTE, reminder.minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            android.util.Log.d("HydrationReminder", "Scheduled for ${reminder.hour}:${reminder.minute}, triggerAt=${calendar.timeInMillis}")
        } catch (e: SecurityException) {
            android.util.Log.e("CommLinkViewModel", "Permission denied for hydration reminder alarm", e)
        }
    }

    private fun cancelHydrationReminder(reminder: com.commvault.commlink.domain.model.HydrationReminder, context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.commvault.commlink.receiver.HydrationReminderReceiver::class.java)
        val requestCode = reminder.id.hashCode() + 5000
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // --- BLUETOOTH MAC ADDRESS SPOOFING (ROOT) ---
    private val _originalBluetoothMac = MutableStateFlow<String?>(null)
    val originalBluetoothMac: StateFlow<String?> = _originalBluetoothMac.asStateFlow()

    private val _currentBluetoothMac = MutableStateFlow("")
    val currentBluetoothMac: StateFlow<String> = _currentBluetoothMac.asStateFlow()

    private val _isMacSpoofed = MutableStateFlow(false)
    val isMacSpoofed: StateFlow<Boolean> = _isMacSpoofed.asStateFlow()

    private val _macSpoofStatus = MutableStateFlow("")
    val macSpoofStatus: StateFlow<String> = _macSpoofStatus.asStateFlow()

    private fun executeRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val result = process.inputStream.bufferedReader().readText().trim()
            val error = process.errorStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank()) result else {
                Log.e("MacSpoof", "Root command error: $error")
                null
            }
        } catch (e: Exception) {
            Log.e("MacSpoof", "Root command failed: ${e.message}", e)
            null
        }
    }

    fun readCurrentBluetoothMac() {
        viewModelScope.launch(Dispatchers.IO) {
            val mac = executeRootCommand("settings get secure bluetooth_address")
            if (mac != null && mac.contains(":")) {
                _currentBluetoothMac.value = mac.uppercase()
                if (_originalBluetoothMac.value == null) {
                    _originalBluetoothMac.value = mac.uppercase()
                }
            } else {
                // Fallback: try reading from BluetoothAdapter
                try {
                    val adapterMac = bluetoothAdapter?.address ?: "Unknown"
                    _currentBluetoothMac.value = adapterMac.uppercase()
                    if (_originalBluetoothMac.value == null) {
                        _originalBluetoothMac.value = adapterMac.uppercase()
                    }
                } catch (e: Exception) {
                    _currentBluetoothMac.value = "Unable to read"
                }
            }
        }
    }

    fun spoofBluetoothMac(newMac: String) {
        val formattedMac = newMac.uppercase().trim()
        // Validate MAC format: XX:XX:XX:XX:XX:XX
        val macRegex = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        if (!macRegex.matches(formattedMac)) {
            _macSpoofStatus.value = "Invalid MAC format. Use XX:XX:XX:XX:XX:XX"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _macSpoofStatus.value = "Applying..."

            // Save original if not already saved
            if (_originalBluetoothMac.value == null) {
                val currentMac = executeRootCommand("settings get secure bluetooth_address")
                if (currentMac != null && currentMac.contains(":")) {
                    _originalBluetoothMac.value = currentMac.uppercase()
                }
            }

            // Write new MAC
            val writeResult = executeRootCommand("settings put secure bluetooth_address $formattedMac")

            // Cycle Bluetooth to apply
            executeRootCommand("service call bluetooth_manager 8") // disable
            delay(1500)
            executeRootCommand("service call bluetooth_manager 6") // enable
            delay(2000)

            // Verify
            val verifyMac = executeRootCommand("settings get secure bluetooth_address")
            if (verifyMac != null && verifyMac.uppercase().trim() == formattedMac) {
                _currentBluetoothMac.value = formattedMac
                _isMacSpoofed.value = true
                _macSpoofStatus.value = "MAC address changed successfully!"
            } else {
                _macSpoofStatus.value = "Failed to apply. Root access may be denied."
            }
        }
    }

    fun restoreOriginalMac() {
        val original = _originalBluetoothMac.value ?: return
        if (!_isMacSpoofed.value) return

        kotlin.concurrent.thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure bluetooth_address $original"))
                process.waitFor()

                // Cycle BT
                val disableProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "service call bluetooth_manager 8"))
                disableProcess.waitFor()
                Thread.sleep(1000)
                val enableProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "service call bluetooth_manager 6"))
                enableProcess.waitFor()

                _currentBluetoothMac.value = original
                _isMacSpoofed.value = false
                _macSpoofStatus.value = "Original MAC restored."
            } catch (e: Exception) {
                Log.e("MacSpoof", "Failed to restore original MAC", e)
            }
        }
    }

    // --- SMART FEATURES ---

    // Shake to Launch
    private val _isShakeToLaunchEnabled = MutableStateFlow(prefs.getBoolean("shake_to_launch", false))
    val isShakeToLaunchEnabled: StateFlow<Boolean> = _isShakeToLaunchEnabled.asStateFlow()

    fun setShakeToLaunchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("shake_to_launch", enabled).apply()
        _isShakeToLaunchEnabled.value = enabled
        val context = getApplication<Application>()
        if (enabled) {
            com.commvault.commlink.service.ShakeDetectorService.start(context)
        } else {
            com.commvault.commlink.service.ShakeDetectorService.stop(context)
        }
    }

    // Restore shake service on app start if enabled
    init {
        if (_isShakeToLaunchEnabled.value) {
            com.commvault.commlink.service.ShakeDetectorService.start(getApplication())
        }
    }

    // Auto-Lock on Walk Away
    private val _isAutoLockEnabled = MutableStateFlow(prefs.getBoolean("auto_lock_walkaway", false))
    val isAutoLockEnabled: StateFlow<Boolean> = _isAutoLockEnabled.asStateFlow()
    
    private val _currentSignalStrength = MutableStateFlow(100) // 0 to 100%
    val currentSignalStrength: StateFlow<Int> = _currentSignalStrength.asStateFlow()

    private val _autoLockDistanceThreshold = MutableStateFlow(prefs.getFloat("auto_lock_threshold", 0.0f)) // 0f (Near, 5m) to 1f (Far, 20m)
    val autoLockDistanceThreshold: StateFlow<Float> = _autoLockDistanceThreshold.asStateFlow()

    private var autoLockJob: Job? = null
    private var lastAutoLockTime = 0L
    private val autoLockCooldownMs = 60000L // 60-second cooldown
    private var rssiReceiver: android.content.BroadcastReceiver? = null

    fun setAutoLockDistanceThreshold(value: Float) {
        prefs.edit().putFloat("auto_lock_threshold", value).apply()
        _autoLockDistanceThreshold.value = value
    }

    fun setAutoLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_lock_walkaway", enabled).apply()
        _isAutoLockEnabled.value = enabled
        if (enabled && connectionState.value is com.commvault.commlink.data.bluetooth.HidDeviceManager.ConnectionState.Connected) {
            startAutoLockMonitor()
        } else {
            stopAutoLockMonitor()
        }
    }

    fun startAutoLockMonitor() {
        if (autoLockJob?.isActive == true) return
        if (!_isAutoLockEnabled.value) return

        autoLockJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2000)
            while (isActive && _isAutoLockEnabled.value) {
                if (connectionState.value is com.commvault.commlink.data.bluetooth.HidDeviceManager.ConnectionState.Connected) {
                    var maxRtt = 0L
                    var pingSuccess = true
                    // Burst ping to fill OS buffer slightly and accurately measure RTT blocking when walking away
                    for (i in 1..3) {
                        val startTime = System.currentTimeMillis()
                        val success = hidDeviceManager.sendMouseReportWithResult(0f, 0f, 0, 0)
                        val rtt = System.currentTimeMillis() - startTime
                        if (rtt > maxRtt) maxRtt = rtt
                        if (!success) pingSuccess = false
                    }
                    
                    if (pingSuccess) {
                        val percentage = (100 - (maxRtt * 2)).toInt().coerceIn(0, 100)
                        _currentSignalStrength.value = percentage
                        
                        // thresholdMs range: 10ms (Near) to 100ms (Far)
                        val thresholdMs = 10 + (_autoLockDistanceThreshold.value * 90).toLong()
                        
                        if (maxRtt > thresholdMs) {
                            val now = System.currentTimeMillis()
                            if (now - lastAutoLockTime > autoLockCooldownMs) {
                                android.util.Log.d("AutoLock", "High latency burst ($maxRtt ms > $thresholdMs ms). Locking PC.")
                                hidDeviceManager.sendKeyPress(
                                    com.commvault.commlink.domain.model.HidKeyCodes.KEY_L,
                                    com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_GUI,
                                    useSticky = false
                                )
                                lastAutoLockTime = now
                            }
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastAutoLockTime > autoLockCooldownMs) {
                            android.util.Log.d("AutoLock", "Ping Failed. Attempting to lock PC before disconnect.")
                            hidDeviceManager.sendKeyPress(
                                com.commvault.commlink.domain.model.HidKeyCodes.KEY_L,
                                com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_GUI,
                                useSticky = false
                            )
                            lastAutoLockTime = now
                        }
                        _currentSignalStrength.value = 0
                    }
                } else {
                    _currentSignalStrength.value = 0
                }
                delay(1000) // Poll more frequently to catch drops before link supervision timeout
            }
        }
    }

    fun stopAutoLockMonitor() {
        autoLockJob?.cancel()
        autoLockJob = null
        rssiReceiver?.let {
            try { getApplication<Application>().unregisterReceiver(it) } catch (e: Exception) {}
            rssiReceiver = null
        }
        _currentSignalStrength.value = 100
    }

    // Start/stop auto-lock monitor when connection state changes
    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is com.commvault.commlink.data.bluetooth.HidDeviceManager.ConnectionState.Connected && _isAutoLockEnabled.value) {
                    startAutoLockMonitor()
                } else if (state is com.commvault.commlink.data.bluetooth.HidDeviceManager.ConnectionState.Disconnected) {
                    stopAutoLockMonitor()
                }
            }
        }
    }
}
data class TextSnippet(
    val name: String,
    val content: String,
    val category: String = "General"
)

data class PasswordEntry(
    val id: String,
    val name: String,
    val username: String? = null,
    val value: String,
    val category: String = "General"
)

