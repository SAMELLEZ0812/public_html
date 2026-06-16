package com.willi.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class ClaudeApiClient(private val apiKey: String) {

    sealed class ApiError(message: String) : Exception(message) {
        class NoApiKey       : ApiError("CLÉ API MANQUANTE")
        class InvalidKey     : ApiError("CLÉ API INVALIDE")
        class QuotaExceeded  : ApiError("QUOTA DÉPASSÉ")
        class Overloaded     : ApiError("SERVEURS SURCHARGÉS")
        class ServerError(code: Int) : ApiError("ERREUR SERVEUR $code")
        class NoInternet     : ApiError("PAS DE CONNEXION INTERNET")
        class Timeout        : ApiError("DÉLAI D'ATTENTE DÉPASSÉ")
        class SslError       : ApiError("ERREUR SSL/RÉSEAU")
        class Unknown(msg: String) : ApiError(msg)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val MEDIA_JSON = "application/json".toMediaType()
    private val API_URL    = "https://api.anthropic.com/v1/messages"
    private val MODEL      = "claude-sonnet-4-6"

    data class Message(val role: String, val content: String)

    suspend fun chat(
        systemPrompt: String,
        conversationHistory: List<Message>,
        userMessage: String,
        retryOnOverload: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) {
            return@withContext Result.failure(ApiError.NoApiKey())
        }

        try {
            val messages = JSONArray().apply {
                conversationHistory.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
                put(JSONObject().put("role", "user").put("content", userMessage))
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 4096)
                put("system", systemPrompt)
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody(MEDIA_JSON))
                .build()

            val response = http.newCall(request).execute()
            val rawBody  = response.body?.string() ?: ""

            when (response.code) {
                200 -> {
                    val text = JSONObject(rawBody)
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                    Result.success(text)
                }
                401 -> Result.failure(ApiError.InvalidKey())
                429 -> {
                    if (retryOnOverload) {
                        delay(3000)
                        chat(systemPrompt, conversationHistory, userMessage, retryOnOverload = false)
                    } else {
                        Result.failure(ApiError.QuotaExceeded())
                    }
                }
                529 -> {
                    if (retryOnOverload) {
                        delay(5000)
                        chat(systemPrompt, conversationHistory, userMessage, retryOnOverload = false)
                    } else {
                        Result.failure(ApiError.Overloaded())
                    }
                }
                in 500..599 -> {
                    if (retryOnOverload) {
                        delay(4000)
                        chat(systemPrompt, conversationHistory, userMessage, retryOnOverload = false)
                    } else {
                        Result.failure(ApiError.ServerError(response.code))
                    }
                }
                else -> Result.failure(ApiError.Unknown("Code ${response.code}: $rawBody"))
            }

        } catch (e: UnknownHostException)   { Result.failure(ApiError.NoInternet()) }
        catch  (e: SocketTimeoutException)  { Result.failure(ApiError.Timeout()) }
        catch  (e: SSLHandshakeException)   { Result.failure(ApiError.SslError()) }
        catch  (e: Exception)               { Result.failure(ApiError.Unknown(e.message ?: "Erreur inconnue")) }
    }
}
