package com.commvault.commlink.agent

import android.content.Context
import android.provider.Settings
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentMessage(
    val text: String,
    val isUser: Boolean,
    val isStatus: Boolean = false,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class AgentViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private val _currentStatus = MutableStateFlow("")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    init {
        // Subscribe to AgentBus — works across Service and UI in same process
        viewModelScope.launch {
            AgentBus.statusFlow.collect { status ->
                handleStatus(status)
            }
        }
    }

    private fun handleStatus(status: AgentStatus) {
        when (status) {
            is AgentStatus.Planning -> {
                _currentStatus.value = "🧠 Planning..."
                _isRunning.value = true
            }
            is AgentStatus.StepUpdate -> {
                val msg = "${status.message} (${status.stepIndex}/${status.totalSteps})"
                _currentStatus.value = msg
                // Update or replace last status message instead of spamming
                val current = _messages.value.toMutableList()
                val lastIdx = current.indexOfLast { it.isStatus }
                if (lastIdx >= 0) {
                    current[lastIdx] = AgentMessage(msg, isUser = false, isStatus = true)
                } else {
                    current.add(AgentMessage(msg, isUser = false, isStatus = true))
                }
                _messages.value = current
            }
            is AgentStatus.Done -> {
                _currentStatus.value = ""
                _isRunning.value = false
                addMessage(AgentMessage("✅ Done: ${status.goal}", isUser = false, isStatus = true))
            }
            is AgentStatus.Error -> {
                _currentStatus.value = ""
                _isRunning.value = false
                addMessage(AgentMessage(status.message, isUser = false, isError = true))
            }
            is AgentStatus.Info -> {
                addMessage(AgentMessage(status.message, isUser = false, isStatus = true))
            }
        }
    }

    fun sendCommand(context: Context, command: String) {
        if (command.isBlank()) return

        addMessage(AgentMessage(command, isUser = true))

        val service = CommLinkAccessibilityService.instance
        if (service == null) {
            _isRunning.value = false
            addMessage(AgentMessage(
                "⚠️ Accessibility Service is not enabled.\n\nTo activate the Agent:\n1. Tap 'Enable' button above\n2. Find 'CommLink Agent'\n3. Toggle it ON",
                isUser = false,
                isError = true
            ))
            return
        }

        _isRunning.value = true
        _currentStatus.value = "🧠 Thinking..."
        service.executeCommand(command)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabled.contains(
                "com.commvault.commlink/com.commvault.commlink.agent.CommLinkAccessibilityService",
                ignoreCase = true
            )
        } catch (e: Exception) {
            false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _isRunning.value = false
        _currentStatus.value = ""
    }

    private fun addMessage(message: AgentMessage) {
        _messages.value = _messages.value + message
    }
}
