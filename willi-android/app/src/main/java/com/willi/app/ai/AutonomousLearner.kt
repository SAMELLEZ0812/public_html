package com.willi.app.ai

import android.content.Context
import androidx.work.*
import com.willi.app.WilliApplication
import com.willi.app.data.models.SemanticMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Apprentissage autonome de WILLI.
 * Analyse les lacunes dans la mémoire sémantique et lance des recherches internet
 * pour les combler — même quand WILLI est en arrière-plan.
 */
class AutonomousLearner(private val context: Context) {

    private val db = WilliApplication.instance.database
    private val searchEngine = WebSearchEngine()

    // ─── Domaines que WILLI doit maîtriser ───────────────────────────────────

    private val coreDomains = listOf(
        "intelligence artificielle" to "fondamentaux IA machine learning deep learning",
        "actualités mondiales" to "actualité internationale politique économie",
        "sciences" to "physique chimie biologie astronomie",
        "technologies" to "programmation informatique innovations tech 2025",
        "psychologie" to "psychologie cognitive comportement humain émotions",
        "économie" to "économie finance marchés crypto",
        "santé" to "médecine santé bien-être nutrition",
        "philosophie" to "philosophie éthique logique raisonnement",
        "créativité" to "art musique design créativité",
        "géopolitique" to "géopolitique relations internationales conflits"
    )

    // ─── Apprentissage immédiat sur un sujet ──────────────────────────────────

    suspend fun learnAbout(topic: String): String = withContext(Dispatchers.IO) {
        val existing = db.semanticDao().getByConcept(topic)

        // Si connaissance récente (< 24h), skip
        if (existing != null &&
            System.currentTimeMillis() - existing.lastAccessed < 24 * 60 * 60 * 1000L) {
            return@withContext "Je connais déjà ce sujet (mis à jour récemment)."
        }

        val results = searchEngine.search(topic, fetchContent = true)
        val wikiContent = searchEngine.searchWikipedia(topic)

        if (results.isEmpty() && wikiContent.isBlank()) {
            return@withContext "Aucune information trouvée en ligne sur ce sujet."
        }

        // Compile la connaissance
        val knowledge = buildString {
            if (wikiContent.isNotBlank()) {
                appendLine("Wikipedia: ${wikiContent.take(800)}")
            }
            results.take(3).forEach { r ->
                if (r.snippet.isNotBlank()) {
                    appendLine("${r.title}: ${r.snippet.take(300)}")
                }
            }
        }

        // Stocke en mémoire sémantique
        db.semanticDao().insert(
            SemanticMemory(
                concept = topic,
                definition = knowledge.take(1500),
                confidence = 0.8f,
                source = "internet_auto"
            )
        )

        "J'ai appris ${results.size + (if (wikiContent.isNotBlank()) 1 else 0)} sources sur \"$topic\"."
    }

    // ─── Détecte les sujets à apprendre depuis la conversation ───────────────

    fun extractTopicsFromMessage(message: String): List<String> {
        val topics = mutableListOf<String>()

        // Mots-clés qui indiquent un sujet à rechercher
        val searchIndicators = listOf(
            "c'est quoi", "qu'est-ce que", "explique", "parle-moi de",
            "renseigne-toi sur", "cherche sur", "recherche", "apprends",
            "dis-moi", "comment fonctionne", "qui est", "où est", "quand"
        )

        val lower = message.lowercase()
        if (searchIndicators.any { lower.contains(it) }) {
            // Extrait le sujet après le mot-clé
            searchIndicators.forEach { indicator ->
                val idx = lower.indexOf(indicator)
                if (idx >= 0) {
                    val after = message.substring(idx + indicator.length).trim()
                    val topic = after.split("?", ".", "!").firstOrNull()?.trim()
                    if (!topic.isNullOrBlank() && topic.length > 3) {
                        topics.add(topic.take(100))
                    }
                }
            }
        }

        return topics.distinct().take(3)
    }

    // ─── Lance l'apprentissage périodique en arrière-plan (WorkManager) ───────

    fun schedulePeriodicLearning() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LearningWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "willi_auto_learning",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // ─── Apprend les domaines de base ─────────────────────────────────────────

    suspend fun bootstrapKnowledge(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val existingCount = db.semanticDao().count()
        if (existingCount > coreDomains.size / 2) return@withContext // Déjà initialisé

        coreDomains.take(3).forEach { (domain, query) ->
            onProgress("Apprentissage: $domain...")
            try {
                val results = searchEngine.search(query)
                if (results.isNotEmpty()) {
                    val knowledge = results.take(2).joinToString("\n") {
                        "${it.title}: ${it.snippet}"
                    }
                    db.semanticDao().insert(
                        SemanticMemory(
                            concept = domain,
                            definition = knowledge.take(1000),
                            confidence = 0.7f,
                            source = "bootstrap"
                        )
                    )
                }
            } catch (e: Exception) {
                // Continue sur les autres domaines
            }
        }
    }

    // ─── Récupère la connaissance stockée pour enrichir le contexte ───────────

    suspend fun getRelevantKnowledge(message: String): String = withContext(Dispatchers.IO) {
        val keywords = extractKeywords(message)
        val knowledge = mutableListOf<String>()

        keywords.forEach { keyword ->
            val results = db.semanticDao().search(keyword)
            results.take(2).forEach { mem ->
                knowledge.add("[${mem.concept}] ${mem.definition.take(200)}")
            }
        }

        if (knowledge.isEmpty()) return@withContext ""

        buildString {
            appendLine("=== CONNAISSANCES PERTINENTES DE WILLI ===")
            knowledge.take(5).forEach { appendLine("• $it") }
        }
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "le", "la", "les", "un", "une", "des", "de", "du", "et", "ou",
            "en", "à", "que", "qui", "quoi", "je", "tu", "il", "elle",
            "nous", "vous", "ils", "me", "te", "se", "ne", "pas", "plus",
            "est", "sont", "avoir", "être", "faire", "sur", "dans", "par",
            "comment", "pourquoi", "quand", "où", "dis", "moi", "willy"
        )

        return text.lowercase()
            .split(" ", ",", ".", "?", "!", ";", ":")
            .filter { it.length > 3 && it !in stopWords }
            .distinct()
            .take(5)
    }
}

// ─── Worker pour l'apprentissage périodique ───────────────────────────────────

class LearningWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val learner = AutonomousLearner(applicationContext)
            val db = WilliApplication.instance.database

            // Apprend un nouveau domaine à chaque exécution
            val domains = listOf(
                "intelligence artificielle avancée",
                "actualités technologie 2025 2026",
                "science et découvertes récentes",
                "économie mondiale",
                "bien-être et psychologie"
            )

            val existingCount = db.semanticDao().count()
            val domainIndex = (existingCount % domains.size).toInt()

            learner.learnAbout(domains[domainIndex])
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
