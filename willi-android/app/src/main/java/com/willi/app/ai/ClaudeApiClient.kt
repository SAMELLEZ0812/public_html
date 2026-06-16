package com.willi.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()
    private val API_URL = "https://api.anthropic.com/v1/messages"
    private val MODEL = "claude-sonnet-4-6"

    data class Message(val role: String, val content: String)

    suspend fun chat(
        systemPrompt: String,
        conversationHistory: List<Message>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()

            conversationHistory.forEach { msg ->
                messages.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }

            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 1024)
                put("system", systemPrompt)
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext Result.failure(
                Exception("Réponse vide du serveur")
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Erreur API: ${response.code} — $responseBody"))
            }

            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
