package com.commvault.commlink.agent

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AgentPlanner {

    private val gson = Gson()

    private val systemPrompt = """
You are CommLink AI Agent — an autonomous phone assistant.
When given a user command, respond ONLY with a JSON object (no markdown, no extra text) in this exact format:

{
  "goal": "brief goal",
  "explanation": "what you will do",
  "steps": [
    {"action": "OPEN_APP", "appName": "WhatsApp"},
    {"action": "WAIT", "milliseconds": 2000},
    {"action": "TAP_TEXT", "text": "Sanjay"},
    {"action": "WAIT", "milliseconds": 1000},
    {"action": "TYPE_TEXT", "text": "hi"},
    {"action": "TAP_SEND"}
  ]
}

Available actions:
- OPEN_APP: {appName}
- GO_HOME, GO_BACK, PRESS_RECENTS
- TAP_TEXT: {text}
- TAP_CONTENT_DESC: {description}
- TYPE_TEXT: {text}
- CLEAR_AND_TYPE: {text}
- TAP_SEND
- SCROLL_DOWN, SCROLL_UP
- WAIT: {milliseconds}
- SEND_WHATSAPP: {contact, message}
- MAKE_CALL: {contact}
- SEND_SMS: {contact, message}
- OPEN_NOTIFICATION_SHADE
- TAKE_SCREENSHOT
- SPEAK: {text}

Rules: Always add WAIT(2000) after OPEN_APP. Keep plans simple and efficient.
""".trimIndent()

    // Multiple free endpoints to try in order
    private val endpoints = listOf(
        "https://text.pollinations.ai/openai/chat/completions" to "openai",
        "https://text.pollinations.ai/openai/chat/completions" to "mistral",
        "https://text.pollinations.ai/openai/chat/completions" to "llama"
    )

    suspend fun planFromCommand(userCommand: String): Result<AgentPlan> = withContext(Dispatchers.IO) {

        // Try POST endpoints first
        for ((url, model) in endpoints) {
            val result = tryPostEndpoint(url, model, userCommand)
            if (result.isSuccess) return@withContext result
            Log.w("AgentPlanner", "Endpoint $url/$model failed, trying next...")
        }

        // Final fallback: GET-based simple endpoint (always works)
        return@withContext tryGetFallback(userCommand)
    }

    private fun tryPostEndpoint(endpointUrl: String, model: String, command: String): Result<AgentPlan> {
        return try {
            val url = URL(endpointUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 30000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val messages = listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to command)
            )

            val body = mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.1,
                "stream" to false
            )

            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(gson.toJson(body)); it.flush()
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                return Result.failure(Exception("HTTP $code from $model"))
            }

            val responseStr = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            connection.disconnect()

            val json = gson.fromJson(responseStr, JsonObject::class.java)
            val content = json
                .getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString ?: return Result.failure(Exception("Empty content"))

            val plan = parsePlan(extractJson(content))
            if (plan.steps.isEmpty()) return Result.failure(Exception("Empty plan"))
            Result.success(plan)

        } catch (e: Exception) {
            Log.w("AgentPlanner", "POST failed for $model: ${e.message}")
            Result.failure(e)
        }
    }

    private fun tryGetFallback(command: String): Result<AgentPlan> {
        return try {
            Log.i("AgentPlanner", "Using GET fallback for: $command")

            // Build a simplified prompt for GET endpoint
            val prompt = """You are a phone automation agent. Reply ONLY with JSON.
Command: "$command"
Reply with: {"goal":"...","explanation":"...","steps":[{"action":"OPEN_APP","appName":"..."},{"action":"WAIT","milliseconds":2000},...]}
Use actions: OPEN_APP, GO_HOME, GO_BACK, TAP_TEXT, TYPE_TEXT, TAP_SEND, WAIT, SEND_WHATSAPP, MAKE_CALL, SCROLL_DOWN, SPEAK"""

            val encoded = URLEncoder.encode(prompt, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encoded?model=openai&json=true")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                // If all fails, build a simple hardcoded plan based on keywords
                return Result.success(buildFallbackPlan(command))
            }

            val text = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            connection.disconnect()

            val plan = parsePlan(extractJson(text.trim()))
            if (plan.steps.isEmpty()) return Result.success(buildFallbackPlan(command))
            Result.success(plan)

        } catch (e: Exception) {
            Log.e("AgentPlanner", "GET fallback failed: ${e.message}")
            // Last resort: keyword-based plan
            Result.success(buildFallbackPlan(command))
        }
    }

    /**
     * Last-resort keyword-based plan when AI is unreachable.
     * Handles the most common commands without needing internet.
     */
    private fun buildFallbackPlan(command: String): AgentPlan {
        val cmd = command.lowercase()

        val steps = mutableListOf<AgentAction>()
        val goal: String

        when {
            cmd.contains("whatsapp") && (cmd.contains("send") || cmd.contains("message") || cmd.contains("msg")) -> {
                goal = "Send WhatsApp message"
                // Extract contact name — word after "to"
                val contact = extractAfter(cmd, listOf("to ", "message ", "send ")) ?: "contact"
                val msg = extractBetween(cmd, listOf("send ", "type ", "say "), listOf(" to ", " on ")) ?: "hello"
                steps += AgentAction.OpenApp("WhatsApp")
                steps += AgentAction.Wait(2500)
                steps += AgentAction.TapContentDesc("Search")
                steps += AgentAction.Wait(500)
                steps += AgentAction.TypeText(contact)
                steps += AgentAction.Wait(1000)
                steps += AgentAction.TapText(contact)
                steps += AgentAction.Wait(1000)
                steps += AgentAction.TypeText(msg)
                steps += AgentAction.Wait(300)
                steps += AgentAction.TapSend()
            }
            cmd.contains("open") || cmd.contains("launch") || cmd.contains("start") -> {
                goal = "Open app"
                val appName = extractAfter(cmd, listOf("open ", "launch ", "start ")) ?: "Settings"
                steps += AgentAction.OpenApp(appName)
                steps += AgentAction.Wait(2000)
            }
            cmd.contains("call") -> {
                goal = "Make a call"
                val contact = extractAfter(cmd, listOf("call ")) ?: ""
                steps += AgentAction.MakeCall(contact)
            }
            cmd.contains("home") -> {
                goal = "Go to home"
                steps += AgentAction.GoHome()
            }
            cmd.contains("back") -> {
                goal = "Go back"
                steps += AgentAction.GoBack()
            }
            cmd.contains("screenshot") || cmd.contains("screen shot") -> {
                goal = "Take screenshot"
                steps += AgentAction.TakeScreenshot()
            }
            cmd.contains("notification") -> {
                goal = "Open notifications"
                steps += AgentAction.OpenNotificationShade()
            }
            cmd.contains("settings") || cmd.contains("wifi") || cmd.contains("bluetooth") -> {
                goal = "Open Settings"
                steps += AgentAction.OpenApp("Settings")
                steps += AgentAction.Wait(2000)
            }
            else -> {
                goal = command
                steps += AgentAction.Speak("I'm not sure how to do that right now. Please check your internet connection and try again.")
            }
        }

        return AgentPlan(
            goal = goal,
            steps = steps,
            explanation = "Using built-in patterns (offline mode)"
        )
    }

    private fun extractAfter(text: String, prefixes: List<String>): String? {
        for (prefix in prefixes) {
            val idx = text.indexOf(prefix)
            if (idx >= 0) {
                val after = text.substring(idx + prefix.length).trim()
                    .split(" ").take(3).joinToString(" ")
                if (after.isNotBlank()) return after
            }
        }
        return null
    }

    private fun extractBetween(text: String, starts: List<String>, ends: List<String>): String? {
        for (start in starts) {
            val startIdx = text.indexOf(start)
            if (startIdx >= 0) {
                val from = startIdx + start.length
                var endIdx = text.length
                for (end in ends) {
                    val e = text.indexOf(end, from)
                    if (e >= 0 && e < endIdx) endIdx = e
                }
                val extracted = text.substring(from, endIdx).trim()
                if (extracted.isNotBlank()) return extracted
            }
        }
        return null
    }

    private fun extractJson(text: String): String {
        var s = text.trim()
        if (s.startsWith("```json")) s = s.removePrefix("```json").trimStart()
        if (s.startsWith("```")) s = s.removePrefix("```").trimStart()
        if (s.endsWith("```")) s = s.removeSuffix("```").trimEnd()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    private fun parsePlan(json: String): AgentPlan {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val goal = obj.get("goal")?.asString ?: "Execute"
            val explanation = obj.get("explanation")?.asString ?: ""
            val stepsArray = obj.getAsJsonArray("steps") ?: return AgentPlan(goal, emptyList(), explanation)

            val steps = mutableListOf<AgentAction>()
            for (el in stepsArray) {
                try {
                    val s = el.asJsonObject
                    val action = s.get("action")?.asString?.uppercase() ?: continue
                    when (action) {
                        "OPEN_APP" -> steps.add(AgentAction.OpenApp(s.get("appName").asString))
                        "GO_HOME" -> steps.add(AgentAction.GoHome())
                        "GO_BACK" -> steps.add(AgentAction.GoBack())
                        "PRESS_RECENTS" -> steps.add(AgentAction.PressRecents())
                        "TAP_TEXT" -> steps.add(AgentAction.TapText(s.get("text").asString))
                        "TAP_CONTENT_DESC" -> steps.add(AgentAction.TapContentDesc(s.get("description").asString))
                        "TYPE_TEXT" -> steps.add(AgentAction.TypeText(s.get("text").asString))
                        "CLEAR_AND_TYPE" -> steps.add(AgentAction.ClearAndType(s.get("text").asString))
                        "TAP_SEND" -> steps.add(AgentAction.TapSend())
                        "SCROLL_DOWN" -> steps.add(AgentAction.ScrollDown())
                        "SCROLL_UP" -> steps.add(AgentAction.ScrollUp())
                        "WAIT" -> steps.add(AgentAction.Wait(s.get("milliseconds")?.asLong ?: 1000L))
                        "SEND_WHATSAPP" -> steps.add(AgentAction.SendWhatsApp(s.get("contact").asString, s.get("message").asString))
                        "MAKE_CALL" -> steps.add(AgentAction.MakeCall(s.get("contact").asString))
                        "SEND_SMS" -> steps.add(AgentAction.SendSms(s.get("contact").asString, s.get("message").asString))
                        "OPEN_NOTIFICATION_SHADE" -> steps.add(AgentAction.OpenNotificationShade())
                        "TAKE_SCREENSHOT" -> steps.add(AgentAction.TakeScreenshot())
                        "SPEAK" -> steps.add(AgentAction.Speak(s.get("text").asString))
                    }
                } catch (e: Exception) {
                    Log.w("AgentPlanner", "Bad step: ${e.message}")
                }
            }
            AgentPlan(goal, steps, explanation)
        } catch (e: Exception) {
            Log.e("AgentPlanner", "Parse failed: ${e.message}")
            AgentPlan("Parse error", emptyList())
        }
    }
}
