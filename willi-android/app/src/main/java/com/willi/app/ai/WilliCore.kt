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
        "est-ce une bonne idée", "que penses-tu de ce projet", "avis sur",
        "ça vaut le coup", "ça vaut le coût", "bonne décision", "mauvaise idée",
        "rentable", "viable", "faisable", "stratégie", "plan d'action"
    )

    private val searchTriggers = listOf(
        "cherche", "recherche", "trouve", "qu'est-ce que", "c'est quoi",
        "renseigne-toi", "apprends", "actualité", "news", "que sais-tu de",
        "parle-moi de", "explique-moi", "dis-moi tout sur", "qui est", "où est",
        "comment fonctionne", "définition de", "prix de", "coût de",
        "dernières nouvelles", "récemment", "aujourd'hui", "cette semaine",
        "qui a gagné", "résultat de", "météo", "cours de", "taux de change",
        "comment faire", "tutoriel", "guide pour", "meilleur moyen de",
        "différence entre", "comparaison", "vs ", "contre ", "lequel est mieux"
    )

    private val diagnosticTriggers = listOf(
        "bug", "erreur", "error", "crash", "plante", "marche pas", "fonctionne pas",
        "ne fonctionne plus", "problème avec", "j'ai un problème", "j'ai un bug",
        "pourquoi ça", "pourquoi mon", "pourquoi ma", "comment réparer",
        "comment régler", "comment résoudre", "ça bug", "ça bloque",
        "mal de", "j'ai mal", "symptôme", "douleur", "depuis hier", "depuis ce matin",
        "mon téléphone", "mon pc", "mon ordinateur", "mon application", "mon code",
        "exception", "null", "undefined", "failed", "cannot", "unable",
        "ne s'allume plus", "ne répond plus", "écran noir", "lent", "freeze",
        "panne", "défaut", "dysfonctionnement", "cassé", "en panne"
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

        // 2. Mode diagnostic si l'utilisateur décrit un problème
        val isDiagnostic = isDiagnosticRequest(userMessage)

        // 3. Recherche internet si nécessaire
        val webContext = if (needsWebSearch(userMessage) || isDiagnostic) {
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
        val basePrompt = personalityEngine.getSystemPrompt(state, creatorName, memorySummary)
        val systemPrompt = if (isDiagnostic) {
            basePrompt + "\n\n[MODE ACTIF: DIAGNOSTIC]\nL'utilisateur décrit un problème. " +
            "Applique la méthode de diagnostic en 5 étapes. Lis attentivement, identifie la cause racine, " +
            "propose une solution concrète. Sois précis comme un expert humain."
        } else basePrompt

        val enrichedMessage = buildEnrichedMessage(userMessage, webContext, storedKnowledge)

        val client = apiClient ?: return WilliResponse(
            text = diagnoseError(ClaudeApiClient.ApiError.NoApiKey(), creatorName),
            emotion = "alerte",
            confidence = 1.0f
        )

        val result = client.chat(systemPrompt, conversationHistory.takeLast(20), enrichedMessage)

        return result.fold(
            onSuccess = { responseText ->
                conversationHistory.add(ClaudeApiClient.Message("user", userMessage))
                conversationHistory.add(ClaudeApiClient.Message("assistant", responseText))

                if (conversationHistory.size > 40) {
                    conversationHistory = conversationHistory.takeLast(40).toMutableList()
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
                val msg = diagnoseError(error, creatorName)
                WilliResponse(text = msg, emotion = "alerte", confidence = 1.0f)
            }
        )
    }

    private fun diagnoseError(error: Throwable, creatorName: String): String = when (error) {
        is ClaudeApiClient.ApiError.NoApiKey ->
            "DIAGNOSTIC — Clé API absente.\n\n" +
            "Mon cerveau n'est pas connecté, $creatorName. " +
            "Lance-moi et entre ta clé API Anthropic (sk-ant-...) dans le setup. " +
            "Tu peux en créer une gratuitement sur console.anthropic.com."

        is ClaudeApiClient.ApiError.InvalidKey ->
            "DIAGNOSTIC — Clé API invalide ou expirée.\n\n" +
            "La clé que tu as entrée ne fonctionne plus, $creatorName. " +
            "Va sur console.anthropic.com → API Keys → Create Key. " +
            "Copie la nouvelle clé et entre-la dans mon setup."

        is ClaudeApiClient.ApiError.QuotaExceeded ->
            "DIAGNOSTIC — Quota API atteint.\n\n" +
            "J'ai consommé toutes les requêtes disponibles sur ton compte. " +
            "Soit le quota se renouvelle dans quelques minutes, soit tu dois recharger " +
            "ton crédit sur console.anthropic.com → Billing."

        is ClaudeApiClient.ApiError.Overloaded ->
            "DIAGNOSTIC — Serveurs Claude surchargés.\n\n" +
            "Les serveurs Anthropic sont en surcharge en ce moment — ce n'est pas de notre côté. " +
            "J'ai déjà réessayé automatiquement. Attends 30 secondes et reparle-moi."

        is ClaudeApiClient.ApiError.ServerError ->
            "DIAGNOSTIC — Erreur serveur Anthropic.\n\n" +
            "Les serveurs de Claude rencontrent un problème technique temporaire. " +
            "J'ai réessayé automatiquement. Si ça persiste, vérifie le statut sur status.anthropic.com."

        is ClaudeApiClient.ApiError.NoInternet ->
            "DIAGNOSTIC — Pas de connexion internet.\n\n" +
            "Je ne peux pas atteindre les serveurs Claude. Vérifie :\n" +
            "1. Ton WiFi ou tes données mobiles sont-ils actifs ?\n" +
            "2. Es-tu en mode avion ?\n" +
            "3. Ta connexion fonctionne-t-elle sur d'autres apps ?\n" +
            "Dès que tu as du réseau, reparle-moi."

        is ClaudeApiClient.ApiError.Timeout ->
            "DIAGNOSTIC — Délai dépassé.\n\n" +
            "La connexion aux serveurs Claude a pris trop longtemps. " +
            "Ça arrive sur les réseaux lents ou instables. Reparle-moi — je réessaierai."

        is ClaudeApiClient.ApiError.SslError ->
            "DIAGNOSTIC — Erreur de sécurité réseau.\n\n" +
            "Problème SSL détecté. Vérifie :\n" +
            "1. L'heure de ton téléphone est-elle correcte ?\n" +
            "2. Es-tu sur un réseau public ou VPN qui bloque les connexions ?\n" +
            "Corrige ça et reparle-moi."

        else ->
            "DIAGNOSTIC — Erreur inattendue.\n\n" +
            "Détail : ${error.message}\n\n" +
            "Si ça se répète, dis-moi exactement ce que tu faisais — " +
            "je vais analyser le problème et trouver une solution."
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

    private fun isDiagnosticRequest(message: String): Boolean {
        val lower = message.lowercase()
        return diagnosticTriggers.any { lower.contains(it) }
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
            "toujours", "jamais", "souviens", "n'oublie pas", "évaluation", "projet",
            "objectif", "but", "rêve", "famille", "travail", "business", "argent",
            "santé", "problème", "aide-moi", "conseil", "décision", "avenir"
        )
        if (importantKeywords.any { userMsg.lowercase().contains(it) }) score += 0.3f
        if (userMsg.length > 80) score += 0.1f
        if (response.length > 300) score += 0.1f
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
