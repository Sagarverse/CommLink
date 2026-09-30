package com.commvault.commlink.agent

import android.util.Log
import com.commvault.commlink.ui.assistant.ChatMessage
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

/**
 * Sends user commands to the AI and gets back a structured AgentPlan.
 * The AI responds with JSON describing what actions to take on the device.
 */
class AgentPlanner {

    private val gson = Gson()

    private val systemPrompt = """
You are CommLink AI Agent — an autonomous phone assistant.
When given a user command, respond ONLY with a JSON object (no markdown, no explanation outside JSON) 
in this exact format:

{
  "goal": "<brief goal description>",
  "explanation": "<what you're going to do in plain language>",
  "steps": [
    {"action": "OPEN_APP", "appName": "WhatsApp"},
    {"action": "WAIT", "milliseconds": 2000},
    {"action": "TAP_TEXT", "text": "Sanjay"},
    {"action": "WAIT", "milliseconds": 1000},
    {"action": "TYPE_TEXT", "text": "hi"},
    {"action": "TAP_SEND"}
  ]
}

Available actions and their JSON fields:
- OPEN_APP: {appName: string}
- GO_HOME: {}
- GO_BACK: {}
- TAP_TEXT: {text: string} — tap element by visible text
- TAP_CONTENT_DESC: {description: string} — tap by accessibility label
- TYPE_TEXT: {text: string} — type into focused input
- CLEAR_AND_TYPE: {text: string} — clear input then type
- TAP_SEND: {} — tap send/submit button
- SCROLL_DOWN: {}
- SCROLL_UP: {}
- WAIT: {milliseconds: number}
- SEND_WHATSAPP: {contact: string, message: string} — shortcut to send WhatsApp
- MAKE_CALL: {contact: string}
- SEND_SMS: {contact: string, message: string}
- OPEN_NOTIFICATION_SHADE: {}
- SPEAK: {text: string} — speak aloud via TTS
- TAKE_SCREENSHOT: {}

Rules:
- Always add WAIT steps between actions to let the UI load
- For WhatsApp messages, always use SEND_WHATSAPP shortcut action when possible
- For any action you're unsure about, add a SPEAK step to inform the user
- Keep plans concise and efficient
""".trimIndent()

    suspend fun planFromCommand(userCommand: String): Result<AgentPlan> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://text.pollinations.ai/openai/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val messages = listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userCommand)
            )

            val requestBody = mapOf(
                "model" to "openai",
                "messages" to messages,
                "temperature" to 0.2,
                "stream" to false
            )

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(gson.toJson(requestBody))
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseStr = reader.use { it.readText() }
                connection.disconnect()

                val jsonResponse = gson.fromJson(responseStr, JsonObject::class.java)
                val content = jsonResponse
                    .getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString ?: ""

                // Extract JSON from response (AI sometimes wraps in code blocks)
                val jsonStr = extractJson(content)
                val plan = parsePlan(jsonStr)
                Result.success(plan)
            } else {
                connection.disconnect()
                Result.failure(Exception("Planner HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e("AgentPlanner", "Planning failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractJson(text: String): String {
        // Strip markdown code fences if present
        var s = text.trim()
        if (s.startsWith("```json")) s = s.removePrefix("```json").trimStart()
        if (s.startsWith("```")) s = s.removePrefix("```").trimStart()
        if (s.endsWith("```")) s = s.removeSuffix("```").trimEnd()
        // Find first { to last }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    private fun parsePlan(json: String): AgentPlan {
        val obj = JsonParser.parseString(json).asJsonObject
        val goal = obj.get("goal")?.asString ?: "Execute command"
        val explanation = obj.get("explanation")?.asString ?: ""
        val stepsArray = obj.getAsJsonArray("steps") ?: return AgentPlan(goal, emptyList(), explanation)

        val steps = mutableListOf<AgentAction>()
        for (stepEl in stepsArray) {
            val step = stepEl.asJsonObject
            val action = step.get("action")?.asString?.uppercase() ?: continue
            try {
                when (action) {
                    "OPEN_APP" -> steps.add(AgentAction.OpenApp(step.get("appName").asString))
                    "GO_HOME" -> steps.add(AgentAction.GoHome())
                    "GO_BACK" -> steps.add(AgentAction.GoBack())
                    "PRESS_RECENTS" -> steps.add(AgentAction.PressRecents())
                    "TAP_TEXT" -> steps.add(AgentAction.TapText(step.get("text").asString))
                    "TAP_CONTENT_DESC" -> steps.add(AgentAction.TapContentDesc(step.get("description").asString))
                    "TYPE_TEXT" -> steps.add(AgentAction.TypeText(step.get("text").asString))
                    "CLEAR_AND_TYPE" -> steps.add(AgentAction.ClearAndType(step.get("text").asString))
                    "TAP_SEND" -> steps.add(AgentAction.TapSend())
                    "SCROLL_DOWN" -> steps.add(AgentAction.ScrollDown())
                    "SCROLL_UP" -> steps.add(AgentAction.ScrollUp())
                    "WAIT" -> steps.add(AgentAction.Wait(step.get("milliseconds")?.asLong ?: 1000L))
                    "SEND_WHATSAPP" -> steps.add(AgentAction.SendWhatsApp(
                        step.get("contact").asString,
                        step.get("message").asString
                    ))
                    "MAKE_CALL" -> steps.add(AgentAction.MakeCall(step.get("contact").asString))
                    "SEND_SMS" -> steps.add(AgentAction.SendSms(
                        step.get("contact").asString,
                        step.get("message").asString
                    ))
                    "OPEN_NOTIFICATION_SHADE" -> steps.add(AgentAction.OpenNotificationShade())
                    "TAKE_SCREENSHOT" -> steps.add(AgentAction.TakeScreenshot())
                    "SPEAK" -> steps.add(AgentAction.Speak(step.get("text").asString))
                    else -> Log.w("AgentPlanner", "Unknown action: $action")
                }
            } catch (e: Exception) {
                Log.w("AgentPlanner", "Failed to parse step $action: ${e.message}")
            }
        }

        return AgentPlan(goal, steps, explanation)
    }
}
