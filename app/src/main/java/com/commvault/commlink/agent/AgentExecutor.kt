package com.commvault.commlink.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Executes a planned sequence of AgentActions on the device
 * using the Accessibility Service.
 */
class AgentExecutor(
    private val service: AccessibilityService,
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit = {}
) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    suspend fun executePlan(plan: AgentPlan) {
        onStatusUpdate("🤖 Starting: ${plan.goal}")
        Log.i("AgentExecutor", "Executing plan: ${plan.goal} (${plan.steps.size} steps)")

        for ((index, step) in plan.steps.withIndex()) {
            onStatusUpdate("Step ${index + 1}/${plan.steps.size}: ${describeAction(step)}")
            Log.d("AgentExecutor", "Step ${index + 1}: $step")

            try {
                executeStep(step)
            } catch (e: Exception) {
                Log.e("AgentExecutor", "Step failed: $step — ${e.message}")
                onStatusUpdate("⚠️ Step failed: ${e.message}")
            }

            // Small breathing delay between steps
            delay(300)
        }

        onStatusUpdate("✅ Done: ${plan.goal}")
    }

    private suspend fun executeStep(action: AgentAction) {
        when (action) {
            is AgentAction.OpenApp -> openApp(action.appName)
            is AgentAction.GoHome -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            is AgentAction.GoBack -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            is AgentAction.PressRecents -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            is AgentAction.TapText -> tapByText(action.text)
            is AgentAction.TapContentDesc -> tapByContentDesc(action.description)
            is AgentAction.TypeText -> typeText(action.text)
            is AgentAction.ClearAndType -> {
                clearText()
                delay(300)
                typeText(action.text)
            }
            is AgentAction.TapSend -> tapSend()
            is AgentAction.ScrollDown -> scrollDown()
            is AgentAction.ScrollUp -> scrollUp()
            is AgentAction.Wait -> delay(action.milliseconds)
            is AgentAction.SendWhatsApp -> sendWhatsApp(action.contact, action.message)
            is AgentAction.MakeCall -> makeCall(action.contact)
            is AgentAction.SendSms -> sendSms(action.contact, action.message)
            is AgentAction.OpenNotificationShade -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            is AgentAction.TakeScreenshot -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            is AgentAction.SetWifi -> {} // Requires system permission on modern Android
            is AgentAction.SetBluetooth -> {} // Requires system permission
            is AgentAction.SetVolume -> {} // TODO: AudioManager
            is AgentAction.Speak -> speak(action.text)
        }
    }

    // ──────────────────────────────────────────────
    // App Launch
    // ──────────────────────────────────────────────

    private fun openApp(appName: String) {
        val pm = context.packageManager
        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "camera" to "com.android.camera2",
            "settings" to "com.android.settings",
            "phone" to "com.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "telegram" to "org.telegram.messenger",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "files" to "com.google.android.apps.nbu.files",
            "play store" to "com.android.vending",
            "facebook" to "com.facebook.katana",
        )

        val normalized = appName.lowercase().trim()
        val packageName = knownPackages[normalized]
            ?: knownPackages.entries.firstOrNull { normalized.contains(it.key) }?.value

        if (packageName != null) {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }

        // Fallback: search all installed apps by label
        val allApps = pm.getInstalledApplications(0)
        val match = allApps.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(normalized)
        }
        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }

        Log.w("AgentExecutor", "Could not find app: $appName")
        onStatusUpdate("⚠️ App not found: $appName")
    }

    // ──────────────────────────────────────────────
    // UI Tree Interaction
    // ──────────────────────────────────────────────

    private fun tapByText(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            return true
        }
        Log.w("AgentExecutor", "Could not find text: $text")
        return false
    }

    private fun tapByContentDesc(description: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findNodeByContentDesc(root, description)
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            return true
        }
        return false
    }

    private fun typeText(text: String) {
        val root = service.rootInActiveWindow ?: return
        // Find focused or editable field
        val editNode = findEditableNode(root)
        if (editNode != null) {
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val bundle = Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            editNode.recycle()
        } else {
            Log.w("AgentExecutor", "No editable field found to type into")
            onStatusUpdate("⚠️ Could not find text input field")
        }
    }

    private fun clearText() {
        val root = service.rootInActiveWindow ?: return
        val editNode = findEditableNode(root) ?: return
        editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        // Select all + delete
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        editNode.recycle()
    }

    private fun tapSend() {
        val root = service.rootInActiveWindow ?: return

        // Try common send button labels
        val sendKeywords = listOf("send", "submit", "post", "done", "go")
        for (keyword in sendKeywords) {
            val node = findNodeByContentDesc(root, keyword)
                ?: findNodeByText(root, keyword.replaceFirstChar { it.uppercase() })
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return
            }
        }

        Log.w("AgentExecutor", "Could not find send button")
    }

    private fun scrollDown() {
        val root = service.rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            ?: run {
                // Fallback: gesture swipe up
                performSwipeGesture(0.5f, 0.7f, 0.5f, 0.3f)
            }
        scrollable?.recycle()
    }

    private fun scrollUp() {
        val root = service.rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            ?: run {
                performSwipeGesture(0.5f, 0.3f, 0.5f, 0.7f)
            }
        scrollable?.recycle()
    }

    private fun performSwipeGesture(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        try {
            val displayMetrics = context.resources.displayMetrics
            val screenW = displayMetrics.widthPixels.toFloat()
            val screenH = displayMetrics.heightPixels.toFloat()

            val path = Path().apply {
                moveTo(fromX * screenW, fromY * screenH)
                lineTo(toX * screenW, toY * screenH)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e("AgentExecutor", "Gesture failed: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────
    // Communication Shortcuts
    // ──────────────────────────────────────────────

    private fun sendWhatsApp(contact: String, message: String) {
        try {
            val encodedMsg = Uri.encode(message)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=&text=$encodedMsg")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // If no phone number, open WhatsApp search
            val searchIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Best effort: open WhatsApp app, then let accessibility find the contact
            openApp("WhatsApp")
            onStatusUpdate("📱 Opened WhatsApp, searching for $contact...")
        } catch (e: Exception) {
            Log.e("AgentExecutor", "WhatsApp send failed: ${e.message}")
            openApp("WhatsApp")
        }
    }

    private fun makeCall(contact: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$contact")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AgentExecutor", "Call failed: ${e.message}")
        }
    }

    private fun sendSms(contact: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$contact")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AgentExecutor", "SMS failed: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────
    // TTS
    // ──────────────────────────────────────────────

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }

    // ──────────────────────────────────────────────
    // Tree Traversal Helpers
    // ──────────────────────────────────────────────

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val results = root.findAccessibilityNodeInfosByText(text)
        return results?.firstOrNull()
    }

    private fun findNodeByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return traverseTree(root) { node ->
            node.contentDescription?.toString()?.lowercase()?.contains(desc.lowercase()) == true
        }
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseTree(root) { node ->
            node.isEditable && node.isEnabled
        }
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseTree(root) { node ->
            node.isScrollable
        }
    }

    private fun traverseTree(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = traverseTree(child, predicate)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun describeAction(action: AgentAction): String = when (action) {
        is AgentAction.OpenApp -> "Opening ${action.appName}"
        is AgentAction.GoHome -> "Going to Home"
        is AgentAction.GoBack -> "Going Back"
        is AgentAction.TapText -> "Tapping \"${action.text}\""
        is AgentAction.TypeText -> "Typing \"${action.text}\""
        is AgentAction.ClearAndType -> "Clearing and typing \"${action.text}\""
        is AgentAction.TapSend -> "Tapping Send"
        is AgentAction.ScrollDown -> "Scrolling Down"
        is AgentAction.ScrollUp -> "Scrolling Up"
        is AgentAction.Wait -> "Waiting ${action.milliseconds}ms"
        is AgentAction.SendWhatsApp -> "Sending WhatsApp to ${action.contact}"
        is AgentAction.MakeCall -> "Calling ${action.contact}"
        is AgentAction.SendSms -> "Sending SMS to ${action.contact}"
        is AgentAction.Speak -> "Speaking: ${action.text}"
        is AgentAction.TakeScreenshot -> "Taking Screenshot"
        is AgentAction.OpenNotificationShade -> "Opening Notifications"
        is AgentAction.PressRecents -> "Opening Recents"
        is AgentAction.TapContentDesc -> "Tapping ${action.description}"
        is AgentAction.GoBack -> "Going Back"
        is AgentAction.GoHome -> "Going Home"
        else -> action.toString()
    }
}
