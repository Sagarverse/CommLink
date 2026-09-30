package com.commvault.commlink.agent

/**
 * Represents a single action the AI agent wants to perform on the device.
 */
sealed class AgentAction {
    // App control
    data class OpenApp(val appName: String) : AgentAction()
    data class GoHome(val dummy: Unit = Unit) : AgentAction()
    data class GoBack(val dummy: Unit = Unit) : AgentAction()
    data class PressRecents(val dummy: Unit = Unit) : AgentAction()

    // UI interaction
    data class TapText(val text: String) : AgentAction()
    data class TapContentDesc(val description: String) : AgentAction()
    data class TypeText(val text: String) : AgentAction()
    data class ClearAndType(val text: String) : AgentAction()
    data class ScrollDown(val dummy: Unit = Unit) : AgentAction()
    data class ScrollUp(val dummy: Unit = Unit) : AgentAction()
    data class TapSend(val dummy: Unit = Unit) : AgentAction()

    // System
    data class SetWifi(val enable: Boolean) : AgentAction()
    data class SetBluetooth(val enable: Boolean) : AgentAction()
    data class TakeScreenshot(val dummy: Unit = Unit) : AgentAction()
    data class SetVolume(val level: Int) : AgentAction()         // 0–100
    data class OpenNotificationShade(val dummy: Unit = Unit) : AgentAction()

    // Communication shortcuts
    data class SendWhatsApp(val contact: String, val message: String) : AgentAction()
    data class MakeCall(val contact: String) : AgentAction()
    data class SendSms(val contact: String, val message: String) : AgentAction()

    // Wait / observe
    data class Wait(val milliseconds: Long) : AgentAction()
    data class Speak(val text: String) : AgentAction()
}

/**
 * A plan the AI returns — an ordered list of actions to execute.
 */
data class AgentPlan(
    val goal: String,
    val steps: List<AgentAction>,
    val explanation: String = ""
)
