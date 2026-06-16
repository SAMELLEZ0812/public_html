package com.willi.app.ai

import com.google.gson.Gson
import com.willi.app.WilliApplication
import com.willi.app.data.database.WilliDatabase
import com.willi.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryManager(private val db: WilliDatabase) {

    private val gson = Gson()

    // ─── Mémoire Épisodique ───────────────────────────────────────────────────

    suspend fun saveEpisode(
        userInput: String,
        williResponse: String,
        emotion: String = "neutre",
        importance: Float = 0.5f,
        tags: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        db.episodicDao().insert(
            EpisodicMemory(
                userInput = userInput,
                williResponse = williResponse,
                emotion = emotion,
                importance = importance,
                tags = gson.toJson(tags)
            )
        )
    }

    suspend fun getRecentEpisodes(limit: Int = 10): List<EpisodicMemory> =
        withContext(Dispatchers.IO) { db.episodicDao().getRecent(limit) }

    suspend fun getImportantEpisodes(): List<EpisodicMemory> =
        withContext(Dispatchers.IO) { db.episodicDao().getImportant() }

    // ─── Mémoire Sémantique ───────────────────────────────────────────────────

    suspend fun learnConcept(concept: String, definition: String, confidence: Float = 1.0f) =
        withContext(Dispatchers.IO) {
            val existing = db.semanticDao().getByConcept(concept)
            if (existing != null) {
                db.semanticDao().update(
                    existing.copy(
                        definition = definition,
                        confidence = (existing.confidence + confidence) / 2f,
                        accessCount = existing.accessCount + 1,
                        lastAccessed = System.currentTimeMillis()
                    )
                )
            } else {
                db.semanticDao().insert(
                    SemanticMemory(concept = concept, definition = definition, confidence = confidence)
                )
            }
        }

    suspend fun searchKnowledge(query: String): List<SemanticMemory> =
        withContext(Dispatchers.IO) { db.semanticDao().search(query) }

    // ─── Mémoire Procédurale ─────────────────────────────────────────────────

    suspend fun learnProcedure(taskName: String, steps: List<String>) =
        withContext(Dispatchers.IO) {
            db.proceduralDao().insert(
                ProceduralMemory(
                    taskName = taskName,
                    steps = gson.toJson(steps)
                )
            )
        }

    // ─── Construction du contexte pour l'IA ──────────────────────────────────

    suspend fun buildContextSummary(): String = withContext(Dispatchers.IO) {
        val recent = db.episodicDao().getRecent(5)
        val important = db.episodicDao().getImportant()
        val episodicCount = db.episodicDao().count()
        val semanticCount = db.semanticDao().count()

        buildString {
            appendLine("=== MÉMOIRE ÉPISODIQUE RÉCENTE ===")
            recent.forEach { ep ->
                appendLine("[${formatTimestamp(ep.timestamp)}] Créateur: ${ep.userInput}")
                appendLine("  WILLI: ${ep.williResponse.take(150)}")
            }

            if (important.isNotEmpty()) {
                appendLine("\n=== MOMENTS IMPORTANTS ===")
                important.take(3).forEach { ep ->
                    appendLine("• ${ep.userInput.take(100)} (importance: ${ep.importance})")
                }
            }

            appendLine("\n=== STATISTIQUES MÉMOIRE ===")
            appendLine("Épisodes mémorisés: $episodicCount")
            appendLine("Concepts appris: $semanticCount")
        }
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE)
        return sdf.format(java.util.Date(ts))
    }
}
