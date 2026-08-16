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
    val connectionState: StateFlow<HidDeviceManager.ConnectionState> = hidDeviceManager.connectionState
    val activeModifiers: StateFlow<Byte> = hidDeviceManager.activeModifiers
    val isTextPushing: StateFlow<Boolean> = hidDeviceManager.isTextPushing

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

    // Assigned Works
    private val _assignedWorks = MutableStateFlow<List<AssignedWork>>(emptyList())
    val assignedWorks: StateFlow<List<AssignedWork>> = _assignedWorks.asStateFlow()

    // To-Do List
    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    // P2P Networking & Chat
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = networkManager.discoveredPeers
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        // Apply initial typing speed to HidDeviceManager
        hidDeviceManager.typingDelay = _typingSpeed.value
        loadMockAssignedWorks()
        loadChatMessages()
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
    }

    fun toggleTodo(todoId: String) {
        _todos.value = _todos.value.map {
            if (it.id == todoId) it.copy(isCompleted = !it.isCompleted) else it
        }
    }

    fun deleteTodo(todoId: String) {
        _todos.value = _todos.value.filter { it.id != todoId }
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

    // Keep Alive Status
    private val _isKeepAliveActive = MutableStateFlow(false)
    val isKeepAliveActive: StateFlow<Boolean> = _isKeepAliveActive.asStateFlow()
    private var keepAliveJob: Job? = null

    // DuckyScript Interpreter Running State
    private val _isMacroRunning = MutableStateFlow(false)
    val isMacroRunning: StateFlow<Boolean> = _isMacroRunning.asStateFlow()

    private val _isMacroPaused = MutableStateFlow(false)
    val isMacroPaused: StateFlow<Boolean> = _isMacroPaused.asStateFlow()
    private var macroJob: Job? = null

    // Snippets list
    private val _snippets = MutableStateFlow<List<TextSnippet>>(emptyList())
    val snippets: StateFlow<List<TextSnippet>> = _snippets.asStateFlow()

    // Password vault list
    private val _passwordEntries = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val passwordEntries: StateFlow<List<PasswordEntry>> = _passwordEntries.asStateFlow()

    init {
        loadSnippets()
        loadPasswordEntries()
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
                if (_isAutoConnectEnabled.value && connectionState.value is HidDeviceManager.ConnectionState.Disconnected) {
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
                // Send a Shift key press every 50 seconds to keep office laptop awake
                sendKey(com.commvault.commlink.domain.model.HidKeyCodes.MODIFIER_LEFT_SHIFT)
                delay(50000L)
            }
        }
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
                        "UP", "UPARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_UP)
                        "DOWN", "DOWNARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_DOWN)
                        "LEFT", "LEFTARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_LEFT)
                        "RIGHT", "RIGHTARROW" -> sendKey(com.commvault.commlink.domain.model.HidKeyCodes.KEY_RIGHT)
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

    fun toggleModifier(modifier: Byte) {
        val current = hidDeviceManager.activeModifiers.value
        val isSet = (current.toInt() and modifier.toInt()) != 0
        hidDeviceManager.setModifier(modifier, !isSet)
    }

    fun sendText(text: String) {
        hidDeviceManager.sendText(text)
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

    fun unlockWindows() {
        val password = secureStorage.getCommvaultPassword() ?: ""
        if (password.isNotEmpty()) {
            hidDeviceManager.unlockWindows(password)
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

    fun savePasswordEntry(name: String, value: String, category: String) {
        val current = _passwordEntries.value.toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        val id = java.util.UUID.randomUUID().toString()
        current.add(PasswordEntry(id, name, value, category))
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
            e.printStackTrace()
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

    fun startIntervalAlarm(title: String, intervalSeconds: Long, repeatCount: Int, context: Context) {
        stopAlarms()
        intervalAlarmJob = viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            while (isActive && count < repeatCount) {
                delay(intervalSeconds * 1000)
                if (!isActive) break
                
                try {
                    val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    val r = android.media.RingtoneManager.getRingtone(context, uri)
                    r.play()
                } catch (e: Exception) {
                    Log.e("CommLinkViewModel", "Error playing notification sound", e)
                }
                
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Alarm Triggered: $title", android.widget.Toast.LENGTH_LONG).show()
                }
                count++
            }
        }
    }

    fun startEscalatingAlarm(maxVolume: Int, context: Context) {
        stopAlarms()
        escalatingAlarmJob = viewModelScope.launch(Dispatchers.IO) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxSystemVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val targetVolume = ((maxVolume / 10f) * maxSystemVolume).toInt().coerceIn(0, maxSystemVolume)

            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0)

            try {
                var uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                if (uri == null) {
                    uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                }
                
                alarmMediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("CommLinkViewModel", "Error playing alarm sound", e)
            }

            var currentVol = 0
            while (isActive && currentVol < targetVolume) {
                delay(3000) // increase volume every 3 seconds
                if (!isActive) break
                currentVol++
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, currentVol, 0)
            }
        }
    }

    fun stopAlarms() {
        intervalAlarmJob?.cancel()
        escalatingAlarmJob?.cancel()
        try {
            alarmMediaPlayer?.stop()
            alarmMediaPlayer?.release()
        } catch (e: Exception) {}
        alarmMediaPlayer = null
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
    val value: String,
    val category: String = "General"
)

