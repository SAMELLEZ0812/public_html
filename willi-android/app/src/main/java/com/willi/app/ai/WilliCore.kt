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
    val webSearchEngine = WebSearchEngine()
    val evaluationEngine = EvaluationEngine(db)
    val autonomousLearner = AutonomousLearner(context)

    private var conversationHistory = mutableListOf<ClaudeApiClient.Message>()
    private var apiClient: ClaudeApiClient? = null

    // ─── Mots-clés déclencheurs ───────────────────────────────────────────────

    private val evaluationTriggers = listOf(
        "évalue", "analyse", "taux de réussite", "chances de", "probabilité",
        "penses-tu que ça marchera", "est-ce que ça va marcher", "risques",
        "est-ce une bonne idée", "que penses-tu de ce projet", "avis sur"
    )

    private val searchTriggers = listOf(
        "cherche", "recherche", "trouve", "qu'est-ce que", "c'est quoi",
        "renseigne-toi", "apprends", "actualité", "news", "que sais-tu de",
        "parle-moi de", "explique-moi", "dis-moi tout sur", "qui est", "où est"
    )

    // ─── Initialisation ───────────────────────────────────────────────────────

    suspend fun initialize(apiKey: String) {
        apiClient = ClaudeApiClient(apiKey)
        ensureWilliState()
        autonomousLearner.schedulePeriodicLearning()
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

        // 1. Détecte si l'utilisateur demande une évaluation
        if (isEvaluationRequest(userMessage)) {
            return processEvaluation(userMessage, state, creatorName)
        }

        // 2. Recherche internet si nécessaire
        val webContext = if (needsWebSearch(userMessage)) {
            performWebSearch(userMessage)
        } else ""

        // 3. Apprend les sujets détectés dans le message
        val topicsToLearn = autonomousLearner.extractTopicsFromMessage(userMessage)
        topicsToLearn.forEach { topic ->
            try { autonomousLearner.learnAbout(topic) } catch (_: Exception) {}
        }

        // 4. Récupère les connaissances pertinentes stockées
        val storedKnowledge = autonomousLearner.getRelevantKnowledge(userMessage)

        // 5. Construit le contexte complet
        val memorySummary = memoryManager.buildContextSummary()
        val systemPrompt = personalityEngine.getSystemPrompt(state, creatorName, memorySummary)

        val enrichedMessage = buildEnrichedMessage(userMessage, webContext, storedKnowledge)

        val client = apiClient ?: return WilliResponse(
            text = "Je n'ai pas encore de connexion à mon cerveau... Il me faut une clé API, ${creatorName}.",
            emotion = "confus",
            confidence = 1.0f
        )

        val result = client.chat(systemPrompt, conversationHistory.takeLast(12), enrichedMessage)

        return result.fold(
            onSuccess = { responseText ->
                conversationHistory.add(ClaudeApiClient.Message("user", userMessage))
                conversationHistory.add(ClaudeApiClient.Message("assistant", responseText))

                if (conversationHistory.size > 24) {
                    conversationHistory = conversationHistory.takeLast(24).toMutableList()
                }

                val emotion = personalityEngine.extractEmotion(responseText)
                val confidence = personalityEngine.extractConfidence(responseText)

                memoryManager.saveEpisode(
                    userInput = userMessage,
                    williResponse = responseText,
                    emotion = emotion,
                    importance = calculateImportance(userMessage, responseText)
                )

                db.willinStateDao().incrementInteractions()
                db.willinStateDao().updateEmotion(emotion)

                WilliResponse(
                    text = responseText,
                    emotion = emotion,
                    confidence = confidence,
                    hasCuriosity = responseText.contains("?")
                )
            },
            onFailure = { error ->
                WilliResponse(
                    text = "Hmm... je rencontre une difficulté de connexion. ${error.message}",
                    emotion = "confus",
                    confidence = 0.5f
                )
            }
        )
    }

    // ─── Évaluation stratégique ───────────────────────────────────────────────

    private suspend fun processEvaluation(
        userMessage: String,
        state: WilliState,
        creatorName: String
    ): WilliResponse {
        val client = apiClient ?: return WilliResponse(
            text = "Clé API manquante pour l'évaluation.",
            emotion = "confus"
        )

        // Extrait le sujet à évaluer
        val subject = extractEvaluationSubject(userMessage)

        // Recherche des informations en ligne sur le sujet
        val searchResults = try {
            webSearchEngine.search(subject)
        } catch (_: Exception) { emptyList() }

        val webContext = if (searchResults.isNotEmpty()) {
            webSearchEngine.formatResultsForPrompt(searchResults, subject)
        } else ""

        val evalPrompt = evaluationEngine.buildEvaluationPrompt(subject, webContext)

        val result = client.chat(
            "Tu es WILLI en mode analyse stratégique experte. Réponds toujours en JSON valide.",
            emptyList(),
            evalPrompt
        )

        return result.fold(
            onSuccess = { jsonResponse ->
                val evalResult = evaluationEngine.parseEvaluationResponse(jsonResponse)

                if (evalResult != null) {
                    evaluationEngine.saveEvaluation(subject, evalResult)
                    val formattedEval = evaluationEngine.formatEvaluationMessage(evalResult, subject)

                    memoryManager.saveEpisode(
                        userInput = userMessage,
                        williResponse = formattedEval.take(500),
                        emotion = if (evalResult.successRate > 60) "confiant" else "prudent",
                        importance = 0.85f,
                        tags = listOf("évaluation", "analyse")
                    )

                    WilliResponse(
                        text = formattedEval,
                        emotion = if (evalResult.successRate > 60) "confiant" else "prudent",
                        confidence = evalResult.confidence
                    )
                } else {
                    WilliResponse(
                        text = jsonResponse,
                        emotion = "analytique",
                        confidence = 0.7f
                    )
                }
            },
            onFailure = { error ->
                WilliResponse(
                    text = "Erreur lors de l'évaluation: ${error.message}",
                    emotion = "confus"
                )
            }
        )
    }

    // ─── Recherche web ────────────────────────────────────────────────────────

    private suspend fun performWebSearch(message: String): String {
        return try {
            val query = extractSearchQuery(message)
            val results = webSearchEngine.search(query, fetchContent = false)
            if (results.isNotEmpty()) {
                webSearchEngine.formatResultsForPrompt(results, query)
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    // ─── Construction du message enrichi ─────────────────────────────────────

    private fun buildEnrichedMessage(
        userMessage: String,
        webContext: String,
        storedKnowledge: String
    ): String {
        if (webContext.isBlank() && storedKnowledge.isBlank()) return userMessage

        return buildString {
            appendLine(userMessage)
            if (webContext.isNotBlank()) {
                appendLine()
                appendLine(webContext)
            }
            if (storedKnowledge.isNotBlank()) {
                appendLine()
                appendLine(storedKnowledge)
            }
        }
    }

    // ─── Détections ───────────────────────────────────────────────────────────

    private fun isEvaluationRequest(message: String): Boolean {
        val lower = message.lowercase()
        return evaluationTriggers.any { lower.contains(it) }
    }

    private fun needsWebSearch(message: String): Boolean {
        val lower = message.lowercase()
        return searchTriggers.any { lower.contains(it) }
    }

    private fun extractSearchQuery(message: String): String {
        searchTriggers.forEach { trigger ->
            val idx = message.lowercase().indexOf(trigger)
            if (idx >= 0) {
                val after = message.substring(idx + trigger.length).trim()
                val query = after.split("?", ".", "!").firstOrNull()?.trim()
                if (!query.isNullOrBlank() && query.length > 2) return query
            }
        }
        return message.take(100)
    }

    private fun extractEvaluationSubject(message: String): String {
        evaluationTriggers.forEach { trigger ->
            val idx = message.lowercase().indexOf(trigger)
            if (idx >= 0) {
                val after = message.substring(idx + trigger.length).trim()
                val subject = after.split("?", ".", "!").firstOrNull()?.trim()
                if (!subject.isNullOrBlank() && subject.length > 2) return subject
            }
        }
        return message.take(150)
    }

    private fun calculateImportance(userMsg: String, response: String): Float {
        var score = 0.5f
        val importantKeywords = listOf(
            "nom", "appelle", "préfère", "aime", "déteste", "important",
            "toujours", "jamais", "souviens", "n'oublie pas", "évaluation", "projet"
        )
        if (importantKeywords.any { userMsg.lowercase().contains(it) }) score += 0.3f
        if (userMsg.length > 100) score += 0.1f
        return score.coerceIn(0f, 1f)
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
