package com.commvault.commlink.ui.assistant

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class Gpt4AllViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val prefs = application.getSharedPreferences("gpt4all_settings", Context.MODE_PRIVATE)
    private val client = Gpt4AllClient()
    private val hidDeviceManager = HidDeviceManager.getInstance(application)

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            role = "assistant",
            content = "👋 Hello! I am your CommLink AI Assistant. I can write code, answer questions, and **generate images** (try *\"generate an image of a futuristic robot\"*). How can I help you today?"
        )
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLocalPcMode = MutableStateFlow(prefs.getBoolean("is_local_pc_mode", false))
    val isLocalPcMode: StateFlow<Boolean> = _isLocalPcMode.asStateFlow()

    private val _serverHost = MutableStateFlow(prefs.getString("server_host", "192.168.1.100") ?: "192.168.1.100")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _serverPort = MutableStateFlow(prefs.getInt("server_port", 4891))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _systemPrompt = MutableStateFlow(
        prefs.getString("system_prompt", "You are CommLink AI Assistant. Provide helpful, direct, beautifully structured responses with bold headings, markdown tables, and code snippets where relevant.") ?: ""
    )
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", false))
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private var currentJob: Job? = null

    init {
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        val userMessage = ChatMessage(role = "user", content = trimmed)
        val assistantPlaceholderId = java.util.UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantPlaceholderId,
            role = "assistant",
            content = "",
            isStreaming = true
        )

        _messages.value = _messages.value + userMessage + assistantPlaceholder
        _isLoading.value = true

        currentJob = viewModelScope.launch {
            val result = client.streamMessage(
                history = _messages.value.dropLast(1), // exclude the empty assistant placeholder
                userQuery = trimmed,
                isLocalPcMode = _isLocalPcMode.value,
                pcHost = _serverHost.value,
                pcPort = _serverPort.value,
                systemPrompt = _systemPrompt.value,
                onChunk = { streamedContent ->
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantPlaceholderId) {
                            msg.copy(content = streamedContent, isStreaming = true)
                        } else {
                            msg
                        }
                    }
                }
            )

            _isLoading.value = false
            result.onSuccess { finalContent ->
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantPlaceholderId) {
                        msg.copy(content = finalContent, isStreaming = false)
                    } else {
                        msg
                    }
                }
                if (_isTtsEnabled.value && isTtsReady && !client.isImageGenerationRequest(trimmed)) {
                    speak(finalContent)
                }
            }.onFailure { err ->
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantPlaceholderId) {
                        msg.copy(
                            content = "⚠️ Unable to get AI response: ${err.message}. Please check your internet connection.",
                            isStreaming = false,
                            isError = true
                        )
                    } else {
                        msg
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        currentJob?.cancel()
        _isLoading.value = false
        _messages.value = _messages.value.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        stopTts()
    }

    fun toggleTts() {
        val newVal = !_isTtsEnabled.value
        _isTtsEnabled.value = newVal
        prefs.edit().putBoolean("tts_enabled", newVal).apply()
        if (!newVal) {
            stopTts()
        }
    }

    private fun speak(text: String) {
        val cleanText = text.replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted.")
            .replace(Regex("!\\[.*?\\]\\(.*?\\)"), "Image generated.")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
            .replace(Regex("[#*_`>]"), "")
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "gpt_reply")
    }

    private fun stopTts() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    fun clearChat() {
        stopTts()
        _messages.value = listOf(
            ChatMessage(
                role = "assistant",
                content = "Chat cleared! Ask a question or tell me to generate an image."
            )
        )
    }

    fun typeToPc(text: String) {
        hidDeviceManager.sendText(text)
    }

    fun toggleLocalPcMode(enabled: Boolean) {
        _isLocalPcMode.value = enabled
        prefs.edit().putBoolean("is_local_pc_mode", enabled).apply()
    }

    fun updatePcSettings(host: String, port: Int, prompt: String) {
        _serverHost.value = host.trim()
        _serverPort.value = port
        _systemPrompt.value = prompt.trim()
        prefs.edit()
            .putString("server_host", _serverHost.value)
            .putInt("server_port", _serverPort.value)
            .putString("system_prompt", _systemPrompt.value)
            .apply()
    }

    override fun onCleared() {
        stopTts()
        tts?.shutdown()
        super.onCleared()
    }
}
