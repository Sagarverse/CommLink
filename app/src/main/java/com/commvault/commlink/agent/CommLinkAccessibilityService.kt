package com.commvault.commlink.agent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CommLink Accessibility Service — the "hands" of the AI agent.
 *
 * This service:
 * 1. Watches all accessibility events on the device
 * 2. Receives commands from AgentViewModel via Intent
 * 3. Uses AgentExecutor to carry out AI-planned actions
 */
class CommLinkAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var executor: AgentExecutor? = null

    companion object {
        const val ACTION_EXECUTE_PLAN = "com.commvault.commlink.EXECUTE_PLAN"
        const val EXTRA_COMMAND = "command"

        // Global reference so ViewModel can check if running
        var instance: CommLinkAccessibilityService? = null
            private set

        fun isRunning() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        executor = AgentExecutor(
            service = this,
            context = applicationContext,
            onStatusUpdate = { status ->
                Log.i("CommLinkA11y", status)
                // Broadcast status back to UI
                val intent = Intent("com.commvault.commlink.AGENT_STATUS")
                intent.putExtra("status", status)
                sendBroadcast(intent)
            }
        )
        Log.i("CommLinkA11y", "✅ Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We react to commands — not to passive events for now
        // Future: read screen content here for smarter context
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

    /**
     * Called by AgentViewModel to execute a command.
     */
    fun executeCommand(command: String) {
        scope.launch {
            val planner = AgentPlanner()
            Log.i("CommLinkA11y", "Planning command: $command")

            // Broadcast planning state
            val planningIntent = Intent("com.commvault.commlink.AGENT_STATUS")
            planningIntent.putExtra("status", "🧠 Planning: $command")
            sendBroadcast(planningIntent)

            val result = planner.planFromCommand(command)
            result.onSuccess { plan ->
                Log.i("CommLinkA11y", "Plan: ${plan.goal} — ${plan.steps.size} steps")
                executor?.executePlan(plan)
            }.onFailure { error ->
                Log.e("CommLinkA11y", "Planning failed: ${error.message}")
                val errorIntent = Intent("com.commvault.commlink.AGENT_STATUS")
                errorIntent.putExtra("status", "❌ Could not plan: ${error.message}")
                sendBroadcast(errorIntent)
            }
        }
    }
}
