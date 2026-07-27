package com.willi.app.data.database

import android.content.Context
import androidx.room.*
import com.willi.app.data.models.*

@Database(
    entities = [
        EpisodicMemory::class,
        SemanticMemory::class,
        ProceduralMemory::class,
        CreatorProfile::class,
        WilliState::class,
        Goal::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WilliDatabase : RoomDatabase() {

    abstract fun episodicDao(): EpisodicMemoryDao
    abstract fun semanticDao(): SemanticMemoryDao
    abstract fun proceduralDao(): ProceduralMemoryDao
    abstract fun creatorDao(): CreatorProfileDao
    abstract fun willinStateDao(): WilliStateDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: WilliDatabase? = null

        fun getInstance(context: Context): WilliDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    WilliDatabase::class.java,
                    "willi_brain.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface EpisodicMemoryDao {
    @Query("SELECT * FROM episodic_memory ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<EpisodicMemory>

    @Query("SELECT * FROM episodic_memory WHERE tags LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    suspend fun getByTag(tag: String): List<EpisodicMemory>

    @Query("SELECT * FROM episodic_memory WHERE importance >= :minImportance ORDER BY timestamp DESC LIMIT 50")
    suspend fun getImportant(minImportance: Float = 0.7f): List<EpisodicMemory>

    @Insert
    suspend fun insert(memory: EpisodicMemory): Long

    @Query("SELECT COUNT(*) FROM episodic_memory")
    suspend fun count(): Int
}

@Dao
interface SemanticMemoryDao {
    @Query("SELECT * FROM semantic_memory WHERE concept LIKE '%' || :query || '%' OR definition LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<SemanticMemory>

    @Query("SELECT * FROM semantic_memory WHERE concept = :concept LIMIT 1")
    suspend fun getByConcept(concept: String): SemanticMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: SemanticMemory)

    @Update
    suspend fun update(memory: SemanticMemory)

    @Query("SELECT COUNT(*) FROM semantic_memory")
    suspend fun count(): Int
}

@Dao
interface ProceduralMemoryDao {
    @Query("SELECT * FROM procedural_memory WHERE taskName LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ProceduralMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: ProceduralMemory)

    @Update
    suspend fun update(memory: ProceduralMemory)
}

@Dao
interface CreatorProfileDao {
    @Query("SELECT * FROM creator_profile WHERE id = 1")
    suspend fun get(): CreatorProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: CreatorProfile)

    @Query("UPDATE creator_profile SET voiceMfccData = :mfccData WHERE id = 1")
    suspend fun updateVoice(mfccData: String)
}

@Dao
interface WilliStateDao {
    @Query("SELECT * FROM willi_state WHERE id = 1")
    suspend fun get(): WilliState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: WilliState)

    @Query("UPDATE willi_state SET totalInteractions = totalInteractions + 1, lastUpdated = :now WHERE id = 1")
    suspend fun incrementInteractions(now: Long = System.currentTimeMillis())

    @Query("UPDATE willi_state SET currentEmotion = :emotion WHERE id = 1")
    suspend fun updateEmotion(emotion: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE status = 'en_cours' ORDER BY priority DESC")
    suspend fun getActive(): List<Goal>

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)
}
