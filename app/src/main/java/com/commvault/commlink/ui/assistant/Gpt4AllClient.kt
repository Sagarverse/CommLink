package com.commvault.commlink.ui.assistant

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system"
    val content: String,
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

class Gpt4AllClient {

    private val gson = Gson()

    /**
     * Checks if the user is requesting an image generation.
     */
    fun isImageGenerationRequest(query: String): Boolean {
        val q = query.lowercase().trim()
        return q.startsWith("/image") ||
                q.startsWith("generate image") ||
                q.startsWith("generate an image") ||
                q.startsWith("create image") ||
                q.startsWith("create an image") ||
                q.startsWith("draw ") ||
                q.startsWith("paint ") ||
                q.contains("generate a picture") ||
                q.contains("generate picture") ||
                q.contains("generate an image of") ||
                q.contains("draw an image of") ||
                q.contains("create an image of")
    }

    /**
     * Generates a high quality image URL using Pollinations Free Image API (Zero API Keys required).
     */
    fun buildImageUrl(query: String): Pair<String, String> {
        var cleanPrompt = query.trim()
            .removePrefix("/image")
            .removePrefix("generate an image of")
            .removePrefix("generate image of")
            .removePrefix("generate an image")
            .removePrefix("generate image")
            .removePrefix("create an image of")
            .removePrefix("create image of")
            .removePrefix("create an image")
            .removePrefix("create image")
            .removePrefix("draw an image of")
            .removePrefix("draw a picture of")
            .removePrefix("draw ")
            .removePrefix("paint ")
            .trim()

        if (cleanPrompt.isEmpty()) cleanPrompt = "A stunning futuristic cyberpunk city with neon lights"

        val encoded = URLEncoder.encode(cleanPrompt, "UTF-8")
        val seed = System.currentTimeMillis() % 100000
        val imageUrl = "https://image.pollinations.ai/prompt/$encoded?width=1024&height=1024&nologo=true&seed=$seed"
        val markdownText = "🎨 **Generated Image for:** *\"$cleanPrompt\"*\n\n![$cleanPrompt]($imageUrl)"
        return Pair(imageUrl, markdownText)
    }

    /**
     * Streams AI message chunks live (like ChatGPT) directly to the callback.
     */
    suspend fun streamMessage(
        history: List<ChatMessage>,
        userQuery: String,
        isLocalPcMode: Boolean = false,
        pcHost: String = "192.168.1.100",
        pcPort: Int = 4891,
        systemPrompt: String = "You are CommLink AI Assistant. Provide helpful, direct, beautifully structured responses with bold headings, markdown tables, and code snippets where relevant.",
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        // Handle Image generation
        if (isImageGenerationRequest(userQuery)) {
            val (imgUrl, markdownText) = buildImageUrl(userQuery)
            onChunk(markdownText)
            return@withContext Result.success(markdownText)
        }

        if (isLocalPcMode) {
            return@withContext streamFromLocalPc(history, pcHost, pcPort, systemPrompt, onChunk)
        }

        // 1. Try real-time streaming from free endpoint
        val streamResult = streamFromFreeCloud(history, systemPrompt, onChunk)
        if (streamResult.isSuccess && streamResult.getOrNull()?.isNotBlank() == true) {
            return@withContext streamResult
        }

        // 2. Fallback to direct GET + simulated smooth typewriter streaming
        val directResult = fetchDirectAiText(userQuery)
        if (directResult.isSuccess) {
            val fullText = directResult.getOrNull() ?: ""
            simulateSmoothTypewriter(fullText, onChunk)
            return@withContext Result.success(fullText)
        }

        streamResult
    }

    private suspend fun streamFromFreeCloud(
        history: List<ChatMessage>,
        systemPrompt: String,
        onChunk: suspend (String) -> Unit
    ): Result<String> {
        return try {
            val url = URL("https://text.pollinations.ai/openai/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 35000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream, application/json")
            }

            val messagesList = mutableListOf<Map<String, String>>()
            messagesList.add(
                mapOf(
                    "role" to "system",
                    "content" to systemPrompt
                )
            )

            val recent = history.takeLast(8)
            for (msg in recent) {
                if (!msg.isError && msg.content.isNotBlank()) {
                    messagesList.add(mapOf("role" to msg.role, "content" to msg.content))
                }
            }

            val requestBody = mapOf(
                "model" to "openai-fast",
                "messages" to messagesList,
                "temperature" to 0.7,
                "stream" to true
            )

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(gson.toJson(requestBody))
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val accumulated = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.startsWith("data: ")) {
                        val dataStr = trimmedLine.removePrefix("data: ").trim()
                        if (dataStr == "[DONE]") break

                        try {
                            val json = gson.fromJson(dataStr, JsonObject::class.java)
                            val choices = json.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                if (delta != null && delta.has("content")) {
                                    val token = delta.get("content").asString
                                    if (!token.isNullOrEmpty()) {
                                        accumulated.append(token)
                                        var cleanText = accumulated.toString()
                                        if (cleanText.startsWith("Assistant:", ignoreCase = true)) cleanText = cleanText.substring(10).trimStart()
                                        if (cleanText.startsWith("AI:", ignoreCase = true)) cleanText = cleanText.substring(3).trimStart()
                                        
                                        onChunk(cleanText)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Non-json chunk or partial
                        }
                    }
                }
                connection.disconnect()

                val finalOutput = accumulated.toString()
                if (finalOutput.isNotBlank()) {
                    Result.success(finalOutput)
                } else {
                    Result.failure(Exception("No streaming chunks received"))
                }
            } else {
                connection.disconnect()
                Result.failure(Exception("Stream HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.w("Gpt4AllClient", "Stream error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun fetchDirectAiText(query: String): Result<String> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encodedQuery?model=openai-fast")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 25000
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val text = reader.use { it.readText() }
                connection.disconnect()
                Result.success(text.trim())
            } else {
                connection.disconnect()
                Result.failure(Exception("Direct status $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun streamFromLocalPc(
        history: List<ChatMessage>,
        host: String,
        port: Int,
        systemPrompt: String,
        onChunk: suspend (String) -> Unit
    ): Result<String> {
        return try {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
            val url = URL("http://$cleanHost:$port/v1/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 60000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val messagesList = mutableListOf<Map<String, String>>()
            messagesList.add(
                mapOf(
                    "role" to "system",
                    "content" to systemPrompt
                )
            )
            val recent = history.takeLast(10)
            for (msg in recent) {
                if (!msg.isError) {
                    messagesList.add(mapOf("role" to msg.role, "content" to msg.content))
                }
            }

            val requestBody = mapOf(
                "model" to "gpt4all",
                "messages" to messagesList,
                "temperature" to 0.7,
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
                val choices = jsonResponse.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val content = choices[0].asJsonObject.getAsJsonObject("message")?.get("content")?.asString ?: ""
                    simulateSmoothTypewriter(content, onChunk)
                    Result.success(content)
                } else {
                    Result.failure(Exception("No choices from PC"))
                }
            } else {
                connection.disconnect()
                Result.failure(Exception("PC HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun simulateSmoothTypewriter(
        fullText: String,
        onChunk: suspend (String) -> Unit
    ) {
        val words = fullText.split(" ")
        val sb = StringBuilder()
        for (i in words.indices) {
            sb.append(words[i])
            if (i < words.size - 1) sb.append(" ")
            onChunk(sb.toString())
            delay(18) // Smooth, natural ChatGPT-like typing speed
        }
    }
}
