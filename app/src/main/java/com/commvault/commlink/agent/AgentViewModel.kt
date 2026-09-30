package com.commvault.commlink.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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
    val timestamp: Long = System.currentTimeMillis()
)

class AgentViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private val _agentStatus = MutableStateFlow("")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var statusReceiver: BroadcastReceiver? = null

    fun registerStatusReceiver(context: Context) {
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getStringExtra("status") ?: return
                _agentStatus.value = status
                // Add status messages to chat
                addMessage(AgentMessage(status, isUser = false, isStatus = true))
                if (status.startsWith("✅")) {
                    _isRunning.value = false
                }
            }
        }
        context.registerReceiver(
            statusReceiver,
            IntentFilter("com.commvault.commlink.AGENT_STATUS"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterStatusReceiver(context: Context) {
        statusReceiver?.let {
            try { context.unregisterReceiver(it) } catch (e: Exception) { /* ignore */ }
        }
        statusReceiver = null
    }

    fun sendCommand(context: Context, command: String) {
        if (command.isBlank()) return

        addMessage(AgentMessage(command, isUser = true))

        val service = CommLinkAccessibilityService.instance
        if (service == null) {
            addMessage(AgentMessage(
                "⚠️ Accessibility Service not enabled.\n\nPlease go to:\nSettings → Accessibility → Installed Apps → CommLink Agent → Turn ON",
                isUser = false
            ))
            return
        }

        _isRunning.value = true
        addMessage(AgentMessage("🧠 Thinking about how to: \"$command\"...", isUser = false, isStatus = true))
        service.executeCommand(command)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("com.commvault.commlink/com.commvault.commlink.agent.CommLinkAccessibilityService")
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private fun addMessage(message: AgentMessage) {
        _messages.value = _messages.value + message
    }
}
