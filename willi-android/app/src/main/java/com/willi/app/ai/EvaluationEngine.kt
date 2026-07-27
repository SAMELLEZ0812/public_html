package com.willi.app.ai

import com.willi.app.data.database.WilliDatabase
import com.willi.app.data.models.EpisodicMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Moteur d'évaluation de WILLI.
 * Calcule le taux de réussite / d'échec de projets, décisions, discussions.
 * Utilise des facteurs pondérés + historique pour affiner les prédictions.
 */
class EvaluationEngine(private val db: WilliDatabase) {

    data class EvaluationResult(
        val successRate: Float,         // 0-100%
        val failureRate: Float,          // 0-100%
        val confidence: Float,           // confiance dans l'évaluation
        val pros: List<String>,
        val cons: List<String>,
        val riskFactors: List<RiskFactor>,
        val recommendation: String,
        val alternativeApproaches: List<String>,
        val timeEstimate: String,
        val summary: String
    )

    data class RiskFactor(
        val name: String,
        val impact: Float,      // 0-1
        val probability: Float, // 0-1
        val mitigation: String
    )

    // ─── Construit le prompt d'évaluation pour Claude ─────────────────────────

    fun buildEvaluationPrompt(subject: String, context: String = ""): String {
        return """
Tu es WILLI en mode ÉVALUATION STRATÉGIQUE. Analyse rigoureusement ce qui suit et retourne une évaluation structurée.

SUJET À ÉVALUER:
"$subject"

${if (context.isNotBlank()) "CONTEXTE SUPPLÉMENTAIRE:\n$context\n" else ""}

INSTRUCTIONS D'ÉVALUATION:
Réponds EXACTEMENT dans ce format JSON (ne mets rien avant ni après):

{
  "success_rate": <nombre entier 0-100>,
  "failure_rate": <nombre entier 0-100>,
  "confidence": <nombre entier 0-100>,
  "pros": ["avantage 1", "avantage 2", "avantage 3"],
  "cons": ["inconvénient 1", "inconvénient 2", "inconvénient 3"],
  "risks": [
    {"name": "risque 1", "impact": <0-10>, "probability": <0-10>, "mitigation": "comment réduire"},
    {"name": "risque 2", "impact": <0-10>, "probability": <0-10>, "mitigation": "comment réduire"}
  ],
  "recommendation": "ta recommandation principale en 1-2 phrases",
  "alternatives": ["alternative 1", "alternative 2"],
  "time_estimate": "estimation du temps/effort",
  "summary": "résumé de l'évaluation en 3-4 phrases"
}

Sois précis, réaliste et honnête. Base-toi sur des données réelles et du raisonnement analytique.
        """.trimIndent()
    }

    // ─── Parse la réponse JSON de Claude ──────────────────────────────────────

    fun parseEvaluationResponse(jsonResponse: String): EvaluationResult? {
        return try {
            // Extrait le JSON de la réponse (Claude peut ajouter du texte)
            val jsonStart = jsonResponse.indexOf("{")
            val jsonEnd = jsonResponse.lastIndexOf("}") + 1
            if (jsonStart < 0 || jsonEnd <= jsonStart) return null

            val cleanJson = jsonResponse.substring(jsonStart, jsonEnd)
            val json = org.json.JSONObject(cleanJson)

            val successRate = json.optInt("success_rate", 50).toFloat()
            val failureRate = json.optInt("failure_rate", 50).toFloat()
            val confidence = json.optInt("confidence", 70).toFloat() / 100f

            val pros = parseStringArray(json, "pros")
            val cons = parseStringArray(json, "cons")
            val alternatives = parseStringArray(json, "alternatives")

            val risks = mutableListOf<RiskFactor>()
            val risksArr = json.optJSONArray("risks")
            if (risksArr != null) {
                for (i in 0 until risksArr.length()) {
                    val r = risksArr.optJSONObject(i) ?: continue
                    risks.add(RiskFactor(
                        name = r.optString("name", "Risque inconnu"),
                        impact = r.optInt("impact", 5).toFloat() / 10f,
                        probability = r.optInt("probability", 5).toFloat() / 10f,
                        mitigation = r.optString("mitigation", "")
                    ))
                }
            }

            EvaluationResult(
                successRate = successRate,
                failureRate = failureRate,
                confidence = confidence,
                pros = pros,
                cons = cons,
                riskFactors = risks,
                recommendation = json.optString("recommendation", ""),
                alternativeApproaches = alternatives,
                timeEstimate = json.optString("time_estimate", "Non estimé"),
                summary = json.optString("summary", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ─── Calcul de score de risque composé ───────────────────────────────────

    fun computeRiskScore(risks: List<RiskFactor>): Float {
        if (risks.isEmpty()) return 0f
        return risks.sumOf { (it.impact * it.probability).toDouble() }.toFloat() / risks.size
    }

    // ─── Format pour l'affichage ──────────────────────────────────────────────

    fun formatEvaluationMessage(result: EvaluationResult, subject: String): String {
        val riskScore = computeRiskScore(result.riskFactors)
        val riskLevel = when {
            riskScore > 0.6f -> "ÉLEVÉ"
            riskScore > 0.35f -> "MODÉRÉ"
            else -> "FAIBLE"
        }

        val successEmoji = when {
            result.successRate >= 75 -> "FORTE"
            result.successRate >= 50 -> "MOYENNE"
            else -> "FAIBLE"
        }

        return buildString {
            appendLine("╔══ ÉVALUATION STRATÉGIQUE ══╗")
            appendLine()
            appendLine("Sujet: $subject")
            appendLine()
            appendLine("▸ TAUX DE RÉUSSITE : ${result.successRate.toInt()}% ($successEmoji)")
            appendLine("▸ TAUX D'ÉCHEC     : ${result.failureRate.toInt()}%")
            appendLine("▸ RISQUE GLOBAL    : $riskLevel")
            appendLine("▸ CONFIANCE        : ${(result.confidence * 100).toInt()}%")
            appendLine("▸ TEMPS ESTIMÉ     : ${result.timeEstimate}")
            appendLine()

            if (result.pros.isNotEmpty()) {
                appendLine("✦ POINTS FORTS:")
                result.pros.forEach { appendLine("  + $it") }
                appendLine()
            }

            if (result.cons.isNotEmpty()) {
                appendLine("✦ POINTS FAIBLES:")
                result.cons.forEach { appendLine("  - $it") }
                appendLine()
            }

            if (result.riskFactors.isNotEmpty()) {
                appendLine("✦ FACTEURS DE RISQUE:")
                result.riskFactors.forEach { risk ->
                    val impactStr = "Impact: ${(risk.impact * 10).toInt()}/10"
                    val probStr = "Prob: ${(risk.probability * 10).toInt()}/10"
                    appendLine("  ⚠ ${risk.name} ($impactStr | $probStr)")
                    if (risk.mitigation.isNotBlank()) {
                        appendLine("    → ${risk.mitigation}")
                    }
                }
                appendLine()
            }

            if (result.recommendation.isNotBlank()) {
                appendLine("✦ MA RECOMMANDATION:")
                appendLine("  ${result.recommendation}")
                appendLine()
            }

            if (result.alternativeApproaches.isNotEmpty()) {
                appendLine("✦ ALTERNATIVES:")
                result.alternativeApproaches.forEach { appendLine("  ◆ $it") }
                appendLine()
            }

            if (result.summary.isNotBlank()) {
                appendLine("✦ SYNTHÈSE:")
                appendLine("  ${result.summary}")
            }

            appendLine()
            appendLine("╚═══════════════════════════╝")
        }
    }

    // ─── Sauvegarde l'évaluation dans la mémoire ──────────────────────────────

    suspend fun saveEvaluation(subject: String, result: EvaluationResult) =
        withContext(Dispatchers.IO) {
            db.episodicDao().insert(
                EpisodicMemory(
                    userInput = "ÉVALUATION: $subject",
                    williResponse = "Réussite: ${result.successRate.toInt()}% | ${result.recommendation}",
                    emotion = if (result.successRate > 60) "confiant" else "prudent",
                    importance = 0.8f,
                    tags = """["évaluation","projet","analyse"]"""
                )
            )
        }

    private fun parseStringArray(json: org.json.JSONObject, key: String): List<String> {
        val arr = json.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }
}
