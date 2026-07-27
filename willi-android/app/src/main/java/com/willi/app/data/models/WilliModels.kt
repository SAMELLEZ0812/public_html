package com.willi.app.data.models

import androidx.room.*

// ─── Stades de développement ─────────────────────────────────────────────────

enum class DevelopmentStage(val label: String, val monthMin: Int, val monthMax: Int) {
    BIRTH("Naissance", 0, 0),
    INFANT("Nourrisson", 1, 6),
    TODDLER("Bambin", 7, 24),
    CHILD("Enfant", 25, 84),
    TEEN("Adolescent", 85, 180),
    YOUNG_ADULT("Jeune Adulte", 181, 300),
    ADULT("Adulte", 301, Int.MAX_VALUE);

    companion object {
        fun fromMonths(months: Int): DevelopmentStage {
            return values().last { it.monthMin <= months }
        }
    }
}

// ─── Mémoire Épisodique ───────────────────────────────────────────────────────

@Entity(tableName = "episodic_memory")
data class EpisodicMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userInput: String,
    val williResponse: String,
    val emotion: String = "neutre",
    val importance: Float = 0.5f,
    val context: String = "",
    val tags: String = ""      // JSON array de tags
)

// ─── Mémoire Sémantique ───────────────────────────────────────────────────────

@Entity(tableName = "semantic_memory")
data class SemanticMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val concept: String,
    val definition: String,
    val confidence: Float = 1.0f,
    val source: String = "apprentissage",
    val learnedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val lastAccessed: Long = System.currentTimeMillis()
)

// ─── Mémoire Procédurale ─────────────────────────────────────────────────────

@Entity(tableName = "procedural_memory")
data class ProceduralMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskName: String,
    val steps: String,             // JSON array des étapes
    val successRate: Float = 1.0f,
    val timesExecuted: Int = 0,
    val lastUsed: Long = System.currentTimeMillis()
)

// ─── Profil du Créateur ───────────────────────────────────────────────────────

@Entity(tableName = "creator_profile")
data class CreatorProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val voiceProfilePath: String = "",
    val voiceMfccData: String = "",  // JSON des features MFCC
    val preferences: String = "{}",  // JSON
    val firstMeetAt: Long = System.currentTimeMillis()
)

// ─── État de WILLI ────────────────────────────────────────────────────────────

@Entity(tableName = "willi_state")
data class WilliState(
    @PrimaryKey val id: Int = 1,
    val birthTimestamp: Long = System.currentTimeMillis(),
    val totalInteractions: Int = 0,
    val currentEmotion: String = "curieux",
    val confidenceLevel: Float = 0.3f,
    val personalityTraits: String = "{}", // JSON
    val knowledgeDomains: String = "{}",  // JSON domain -> niveau
    val lastUpdated: Long = System.currentTimeMillis()
)

// ─── Objectifs / Planification ────────────────────────────────────────────────

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val steps: String,          // JSON array
    val status: String = "en_cours",  // en_cours, complété, abandonné
    val priority: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

// ─── Message de chat ─────────────────────────────────────────────────────────

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isWilli: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String = "neutre",
    val confidence: Float = 1.0f
)

// ─── Réponse WILLI ───────────────────────────────────────────────────────────

data class WilliResponse(
    val text: String,
    val emotion: String = "neutre",
    val confidence: Float = 1.0f,
    val hasCuriosity: Boolean = false,
    val curiosityQuestion: String? = null,
    val internalThought: String = "",
    val actionRequired: String? = null
)
