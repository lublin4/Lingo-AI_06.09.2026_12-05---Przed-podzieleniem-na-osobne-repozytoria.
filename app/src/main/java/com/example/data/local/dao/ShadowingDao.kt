package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.ShadowingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShadowingDao {
    @Query("SELECT * FROM shadowing_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ShadowingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ShadowingEntity): Long

    @Query("SELECT * FROM shadowing_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ShadowingEntity?

    @Query("DELETE FROM shadowing_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("""
        SELECT COUNT(*) FROM shadowing_sessions 
        WHERE LOWER(TRIM(foreignPhrase)) = LOWER(TRIM(:phrase)) 
        AND (
            LOWER(TRIM(targetLanguage)) = LOWER(TRIM(:language))
            OR (LOWER(TRIM(:language)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(targetLanguage, 'spanish'))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:language)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(targetLanguage, 'english'))) IN ('english', 'en'))
            OR (LOWER(TRIM(:language)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(targetLanguage, 'french'))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:language)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(targetLanguage, 'german'))) IN ('german', 'de'))
            OR (LOWER(TRIM(:language)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(targetLanguage, 'italian'))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:language)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(targetLanguage, 'russian'))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:language)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(targetLanguage, 'ukrainian'))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:language)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(targetLanguage, 'polish'))) IN ('polish', 'pl'))
        )
    """)
    suspend fun countDuplicate(phrase: String, language: String): Int
}
