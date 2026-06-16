package com.willi.app.ai

import android.content.Context
import com.willi.app.WilliApplication
import com.willi.app.data.database.WilliDatabase
import com.willi.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WilliCore(private val context: Context) {

    private val db = WilliApplication.instance.database
    val memoryManager = MemoryManager(db)
    val personalityEngine = PersonalityEngine()
    val planningEngine = PlanningEngine(db)

    private var conversationHistory = mutableListOf<ClaudeApiClient.Message>()
    private var apiClient: ClaudeApiClient? = null

    // ─── Initialisation ───────────────────────────────────────────────────────

    suspend fun initialize(apiKey: String) {
        apiClient = ClaudeApiClient(apiKey)
        ensureWilliState()
    }

    private suspend fun ensureWilliState() = withContext(Dispatchers.IO) {
        if (db.willinStateDao().get() == null) {
            db.willinStateDao().save(
                WilliState(
                    birthTimestamp = System.currentTimeMillis(),
                    currentEmotion = "curieux",
                    confidenceLevel = 0.2f
                )
            )
        }
    }

    // ─── Traitement d'un message ──────────────────────────────────────────────

    suspend fun processMessage(userMessage: String): WilliResponse {
        val state = db.willinStateDao().get() ?: WilliState()
        val creator = db.creatorDao().get()
        val creatorName = creator?.name?.takeIf { it.isNotBlank() } ?: "mon créateur"
        val memorySummary = memoryManager.buildContextSummary()

        val systemPrompt = personalityEngine.getSystemPrompt(state, creatorName, memorySummary)

        // Détecte si c'est un objectif à planifier
        val isGoalIntent = planningEngine.detectGoalIntent(userMessage)

        val enrichedMessage = if (isGoalIntent && state.totalInteractions > 10) {
            "$userMessage\n\n[Note interne: si c'est un objectif complexe, propose de le décomposer en étapes]"
        } else {
            userMessage
        }

        val client = apiClient ?: return WilliResponse(
            text = "Je n'ai pas encore de connexion à mon cerveau... Il me faut une clé API, ${creatorName}.",
            emotion = "confus",
            confidence = 1.0f
        )

        val result = client.chat(systemPrompt, conversationHistory.takeLast(10), enrichedMessage)

        return result.fold(
            onSuccess = { responseText ->
                // Met à jour l'historique de conversation
                conversationHistory.add(ClaudeApiClient.Message("user", userMessage))
                conversationHistory.add(ClaudeApiClient.Message("assistant", responseText))

                // Garde l'historique court (20 derniers messages)
                if (conversationHistory.size > 20) {
                    conversationHistory = conversationHistory.takeLast(20).toMutableList()
                }

                // Sauvegarde en mémoire épisodique
                val emotion = personalityEngine.extractEmotion(responseText)
                val confidence = personalityEngine.extractConfidence(responseText)

                memoryManager.saveEpisode(
                    userInput = userMessage,
                    williResponse = responseText,
                    emotion = emotion,
                    importance = calculateImportance(userMessage, responseText)
                )

                // Met à jour l'état
                db.willinStateDao().incrementInteractions()
                db.willinStateDao().updateEmotion(emotion)

                WilliResponse(
                    text = responseText,
                    emotion = emotion,
                    confidence = confidence,
                    hasCuriosity = responseText.contains("?") && DevelopmentStage.fromMonths(
                        getAgeInMonths(state.birthTimestamp)
                    ).ordinal <= DevelopmentStage.CHILD.ordinal
                )
            },
            onFailure = { error ->
                WilliResponse(
                    text = "Hmm... je rencontre une difficulté à me connecter. ${error.message}",
                    emotion = "confus",
                    confidence = 0.5f
                )
            }
        )
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private fun calculateImportance(userMsg: String, response: String): Float {
        var score = 0.5f
        val importantKeywords = listOf(
            "nom", "appelle", "préfère", "aime", "déteste", "important",
            "toujours", "jamais", "souviens", "n'oublie pas"
        )
        if (importantKeywords.any { userMsg.lowercase().contains(it) }) score += 0.3f
        if (userMsg.length > 100) score += 0.1f
        if (response.contains("je retiens", ignoreCase = true)) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun getAgeInMonths(birthTimestamp: Long): Int {
        val msPerMonth = 30L * 24 * 60 * 60 * 1000
        return ((System.currentTimeMillis() - birthTimestamp) / msPerMonth).toInt()
    }

    suspend fun getState(): WilliState = withContext(Dispatchers.IO) {
        db.willinStateDao().get() ?: WilliState()
    }

    suspend fun getCreator(): CreatorProfile? = withContext(Dispatchers.IO) {
        db.creatorDao().get()
    }

    fun clearConversationHistory() {
        conversationHistory.clear()
    }
}
