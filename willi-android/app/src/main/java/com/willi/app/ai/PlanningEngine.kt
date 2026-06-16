package com.willi.app.ai

import com.willi.app.data.database.WilliDatabase
import com.willi.app.data.models.Goal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class PlanningEngine(private val db: WilliDatabase) {

    suspend fun createGoal(title: String, description: String, steps: List<String>, priority: Int = 5) =
        withContext(Dispatchers.IO) {
            db.goalDao().insert(
                Goal(
                    title = title,
                    description = description,
                    steps = JSONArray(steps).toString(),
                    priority = priority
                )
            )
        }

    suspend fun getActiveGoals(): List<Goal> = withContext(Dispatchers.IO) {
        db.goalDao().getActive()
    }

    // Détecte si le message contient un objectif à planifier
    fun detectGoalIntent(message: String): Boolean {
        val goalKeywords = listOf(
            "je veux", "j'aimerais", "peux-tu", "aide-moi à", "comment",
            "planifie", "organise", "crée", "fais", "objectif", "but", "projet"
        )
        return goalKeywords.any { message.lowercase().contains(it) }
    }

    // Décompose un objectif complexe en étapes
    fun buildDecompositionPrompt(goal: String): String {
        return """
            L'utilisateur a exprimé cet objectif: "$goal"

            Décompose cet objectif en 3 à 7 étapes concrètes et réalisables.
            Réponds avec une liste numérotée uniquement.
            Commence directement par "1."
        """.trimIndent()
    }

    // Extrait les étapes d'une réponse numérotée
    fun extractSteps(response: String): List<String> {
        val stepPattern = Regex("^\\d+\\.\\s*(.+)$", RegexOption.MULTILINE)
        return stepPattern.findAll(response)
            .map { it.groupValues[1].trim() }
            .toList()
    }
}
