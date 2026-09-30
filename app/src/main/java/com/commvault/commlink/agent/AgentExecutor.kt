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

class AgentExecutor(
    private val service: AccessibilityService,
    private val context: Context
) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
    }

    suspend fun executePlan(plan: AgentPlan) {
        AgentBus.tryEmit(AgentStatus.Info("🤖 Starting: ${plan.goal}"))

        if (plan.steps.isEmpty()) {
            AgentBus.tryEmit(AgentStatus.Error("No steps to execute"))
            return
        }

        for ((index, step) in plan.steps.withIndex()) {
            val desc = describeAction(step)
            AgentBus.tryEmit(AgentStatus.StepUpdate(desc, index + 1, plan.steps.size))
            Log.d("AgentExecutor", "Step ${index + 1}/${plan.steps.size}: $step")

            try {
                executeStep(step)
            } catch (e: Exception) {
                Log.e("AgentExecutor", "Step failed: $step — ${e.message}", e)
                AgentBus.tryEmit(AgentStatus.Info("⚠️ Step issue: ${e.message?.take(50)}"))
            }
            delay(250)
        }

        AgentBus.tryEmit(AgentStatus.Done(plan.goal))
    }

    private suspend fun executeStep(action: AgentAction) {
        when (action) {
            is AgentAction.OpenApp -> openApp(action.appName)
            is AgentAction.GoHome -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            is AgentAction.GoBack -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            is AgentAction.PressRecents -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            is AgentAction.TapText -> {
                val found = tapByText(action.text)
                if (!found) AgentBus.tryEmit(AgentStatus.Info("🔍 Looking for \"${action.text}\"..."))
            }
            is AgentAction.TapContentDesc -> tapByContentDesc(action.description)
            is AgentAction.TypeText -> typeText(action.text)
            is AgentAction.ClearAndType -> { clearText(); delay(200); typeText(action.text) }
            is AgentAction.TapSend -> tapSend()
            is AgentAction.ScrollDown -> scrollDown()
            is AgentAction.ScrollUp -> scrollUp()
            is AgentAction.Wait -> delay(action.milliseconds)
            is AgentAction.SendWhatsApp -> sendWhatsApp(action.contact, action.message)
            is AgentAction.MakeCall -> makeCall(action.contact)
            is AgentAction.SendSms -> sendSms(action.contact, action.message)
            is AgentAction.OpenNotificationShade -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            is AgentAction.TakeScreenshot -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            is AgentAction.Speak -> speak(action.text)
            else -> Log.w("AgentExecutor", "Unhandled action: $action")
        }
    }

    // ── App Launch ──────────────────────────────────────

    private suspend fun openApp(appName: String) {
        val pm = context.packageManager
        val known = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "settings" to "com.android.settings",
            "phone" to "com.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "telegram" to "org.telegram.messenger",
            "instagram" to "com.instagram.android",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "camera" to "com.android.camera2"
        )

        val normalized = appName.lowercase().trim()
        val packageName = known[normalized]
            ?: known.entries.firstOrNull { normalized.contains(it.key) }?.value
            ?: findPackageByLabel(appName)

        if (packageName != null) {
            val intent = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (intent != null) {
                context.startActivity(intent)
                // Wait for app to open
                delay(2500)
                return
            }
        }

        AgentBus.tryEmit(AgentStatus.Info("⚠️ App not found: $appName"))
    }

    private fun findPackageByLabel(appName: String): String? {
        val pm = context.packageManager
        return pm.getInstalledApplications(0).firstOrNull { app ->
            pm.getApplicationLabel(app).toString().lowercase().contains(appName.lowercase())
        }?.packageName
    }

    // ── UI Interaction ───────────────────────────────────

    private fun getRoot(): AccessibilityNodeInfo? {
        return try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            null
        }
    }

    private fun tapByText(text: String): Boolean {
        val root = getRoot() ?: return false
        // Try exact match first, then contains
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val node = nodes?.firstOrNull { it.isClickable || it.isEnabled }
            ?: nodes?.firstOrNull()
            ?: traverseTree(root) { n ->
                n.text?.toString()?.contains(text, ignoreCase = true) == true && n.isClickable
            }

        return if (node != null) {
            // Try clicking the node or its clickable parent
            val clicked = clickNodeOrParent(node)
            node.recycle()
            clicked
        } else {
            false
        }
    }

    private fun tapByContentDesc(description: String): Boolean {
        val root = getRoot() ?: return false
        val node = traverseTree(root) { n ->
            n.contentDescription?.toString()?.contains(description, ignoreCase = true) == true
        } ?: return false
        val clicked = clickNodeOrParent(node)
        node.recycle()
        return clicked
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Walk up to find clickable parent
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            val next = parent.parent
            parent.recycle()
            parent = next
            depth++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun typeText(text: String) {
        val root = getRoot() ?: run {
            AgentBus.tryEmit(AgentStatus.Info("⚠️ Can't access screen"))
            return
        }

        val editNode = findEditableNode(root)
        if (editNode != null) {
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            editNode.recycle()
            if (!success) {
                // Fallback: paste via clipboard
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
                editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
        } else {
            AgentBus.tryEmit(AgentStatus.Info("⚠️ No text field found on screen"))
        }
    }

    private fun clearText() {
        val root = getRoot() ?: return
        val editNode = findEditableNode(root) ?: return
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        editNode.recycle()
    }

    private fun tapSend() {
        val root = getRoot() ?: return
        val sendKeywords = listOf("send", "submit", "post", "done", "go", "ok")
        for (kw in sendKeywords) {
            val byDesc = traverseTree(root) { n ->
                n.contentDescription?.toString()?.contains(kw, ignoreCase = true) == true
            }
            if (byDesc != null) {
                byDesc.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                byDesc.recycle()
                return
            }
            val byText = root.findAccessibilityNodeInfosByText(kw.replaceFirstChar { it.uppercase() })
            val node = byText?.firstOrNull()
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return
            }
        }
        AgentBus.tryEmit(AgentStatus.Info("⚠️ Couldn't find Send button"))
    }

    private fun scrollDown() {
        val root = getRoot() ?: return
        findScrollableNode(root)?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            it.recycle()
        } ?: performSwipeGesture(0.5f, 0.7f, 0.5f, 0.3f)
    }

    private fun scrollUp() {
        val root = getRoot() ?: return
        findScrollableNode(root)?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            it.recycle()
        } ?: performSwipeGesture(0.5f, 0.3f, 0.5f, 0.7f)
    }

    private fun performSwipeGesture(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        try {
            val dm = context.resources.displayMetrics
            val path = Path().apply {
                moveTo(fromX * dm.widthPixels, fromY * dm.heightPixels)
                lineTo(toX * dm.widthPixels, toY * dm.heightPixels)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
                .build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e("AgentExecutor", "Swipe failed: ${e.message}")
        }
    }

    // ── Communication ────────────────────────────────────

    private suspend fun sendWhatsApp(contact: String, message: String) {
        // Open WhatsApp
        openApp("WhatsApp")
        delay(1000)

        // Try to find the contact in search
        val root = getRoot()
        if (root != null) {
            // Tap search icon
            val searchNode = traverseTree(root) { n ->
                n.contentDescription?.toString()?.contains("search", ignoreCase = true) == true ||
                n.contentDescription?.toString()?.contains("Search", ignoreCase = true) == true
            }
            searchNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(800)

            // Type contact name
            typeText(contact)
            delay(1000)

            // Tap on the contact result
            tapByText(contact)
            delay(1000)

            // Type the message
            typeText(message)
            delay(500)

            // Tap send
            tapSend()
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

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // ── Tree Traversal ───────────────────────────────────

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseTree(root) { n -> n.isEditable && n.isEnabled && n.isFocusable }
            ?: traverseTree(root) { n -> n.isEditable }
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseTree(root) { n -> n.isScrollable }
    }

    private fun traverseTree(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        return try {
            if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = traverseTree(child, predicate)
                child.recycle()
                if (result != null) return result
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun describeAction(action: AgentAction): String = when (action) {
        is AgentAction.OpenApp -> "📱 Opening ${action.appName}"
        is AgentAction.GoHome -> "🏠 Going Home"
        is AgentAction.GoBack -> "⬅️ Going Back"
        is AgentAction.TapText -> "👆 Tapping \"${action.text}\""
        is AgentAction.TypeText -> "⌨️ Typing \"${action.text}\""
        is AgentAction.ClearAndType -> "⌨️ Typing \"${action.text}\""
        is AgentAction.TapSend -> "📤 Sending"
        is AgentAction.ScrollDown -> "⬇️ Scrolling Down"
        is AgentAction.ScrollUp -> "⬆️ Scrolling Up"
        is AgentAction.Wait -> "⏳ Waiting..."
        is AgentAction.SendWhatsApp -> "💬 Sending WhatsApp to ${action.contact}"
        is AgentAction.MakeCall -> "📞 Calling ${action.contact}"
        is AgentAction.SendSms -> "💬 SMS to ${action.contact}"
        is AgentAction.Speak -> "🔊 ${action.text}"
        is AgentAction.TakeScreenshot -> "📸 Screenshot"
        is AgentAction.OpenNotificationShade -> "🔔 Opening Notifications"
        is AgentAction.PressRecents -> "📋 Recents"
        is AgentAction.TapContentDesc -> "👆 Tapping ${action.description}"
        else -> "$action"
    }
}
