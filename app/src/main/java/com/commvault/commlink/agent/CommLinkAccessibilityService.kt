package com.commvault.commlink.agent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommLinkAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var executor: AgentExecutor? = null

    companion object {
        var instance: CommLinkAccessibilityService? = null
            private set

        fun isRunning() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        executor = AgentExecutor(service = this, context = applicationContext)
        Log.i("CommLinkA11y", "✅ Accessibility Service connected")
        scope.launch {
            AgentBus.emit(AgentStatus.Info("✅ Agent ready! Tell me what to do."))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Used for future screen-reading features
    }

    override fun onInterrupt() {
        Log.w("CommLinkA11y", "Service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        executor?.destroy()
        executor = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        executor?.destroy()
        super.onDestroy()
    }

    fun executeCommand(command: String) {
        scope.launch(Dispatchers.IO) {
            AgentBus.emit(AgentStatus.Planning(command))
            Log.i("CommLinkA11y", "Planning: $command")

            val planner = AgentPlanner()
            val result = planner.planFromCommand(command)

            result.onSuccess { plan ->
                Log.i("CommLinkA11y", "Plan ready: ${plan.goal} — ${plan.steps.size} steps")
                AgentBus.emit(AgentStatus.Info("📋 Plan: ${plan.explanation.ifBlank { plan.goal }}"))
                // Switch to main thread for accessibility actions
                launch(Dispatchers.Main) {
                    executor?.executePlan(plan)
                }
            }.onFailure { error ->
                Log.e("CommLinkA11y", "Planning failed: ${error.message}")
                AgentBus.emit(AgentStatus.Error("❌ Couldn't plan: ${error.message}"))
            }
        }
    }
}
