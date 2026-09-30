package com.commvault.commlink.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process event bus for agent status updates.
 * Using SharedFlow instead of broadcasts avoids Android 14+ broadcast restrictions.
 */
object AgentBus {
    private val _statusFlow = MutableSharedFlow<AgentStatus>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val statusFlow = _statusFlow.asSharedFlow()

    suspend fun emit(status: AgentStatus) {
        _statusFlow.emit(status)
    }

    fun tryEmit(status: AgentStatus) {
        _statusFlow.tryEmit(status)
    }
}

sealed class AgentStatus {
    data class Planning(val command: String) : AgentStatus()
    data class StepUpdate(val message: String, val stepIndex: Int, val totalSteps: Int) : AgentStatus()
    data class Done(val goal: String) : AgentStatus()
    data class Error(val message: String) : AgentStatus()
    data class Info(val message: String) : AgentStatus()
}
