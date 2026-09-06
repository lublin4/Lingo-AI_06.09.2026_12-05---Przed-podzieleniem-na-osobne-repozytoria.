package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TutorDao {

    // --- User Goal ---
    @Query("SELECT * FROM user_goals WHERE id = 1 LIMIT 1")
    fun getGoal(): Flow<UserGoal?>

    @Query("SELECT * FROM user_goals WHERE id = 1 LIMIT 1")
    suspend fun getGoalSync(): UserGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: UserGoal)

    // --- Sessions ---
    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)

    @Query("DELETE FROM mistakes WHERE sessionId = :sessionId")
    suspend fun deleteMistakesBySessionId(sessionId: Long)

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentSessionsSync(): List<Session>

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    // --- Vocabulary ---
    @Query("""
        SELECT * FROM vocabulary 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'spanish')))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'english')))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'french')))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'german')))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'italian')))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'russian')))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'ukrainian')))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'polish')))) IN ('polish', 'pl'))
        ) 
        AND IFNULL(interference_tag, '') NOT IN ('LANGUAGE_ISLAND', 'ACCELERATOR_NODE') 
        ORDER BY dateAdded DESC
    """)
    fun getAllVocabulary(languageCode: String): Flow<List<Vocabulary>>

    @Query("""
        SELECT * FROM vocabulary 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'spanish')))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'english')))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'french')))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'german')))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'italian')))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'russian')))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'ukrainian')))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'polish')))) IN ('polish', 'pl'))
        ) 
        AND IFNULL(interference_tag, '') IN ('LANGUAGE_ISLAND', 'ACCELERATOR_NODE') 
        ORDER BY dateAdded DESC
    """)
    fun getLanguageIslands(languageCode: String): Flow<List<Vocabulary>>

    @Query("""
        SELECT * FROM vocabulary 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'spanish')))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'english')))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'french')))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'german')))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'italian')))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'russian')))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'ukrainian')))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'polish')))) IN ('polish', 'pl'))
        ) 
        AND IFNULL(interference_tag, '') NOT IN ('LANGUAGE_ISLAND', 'ACCELERATOR_NODE') 
        ORDER BY dateAdded DESC
    """)
    fun getVocabularyForLanguage(languageCode: String): Flow<List<Vocabulary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(vocab: Vocabulary): Long

    @androidx.room.Transaction
    suspend fun insertVocabularyTransaction(vocab: Vocabulary): Long {
        return insertVocabulary(vocab)
    }

    @Update
    suspend fun updateVocabulary(vocab: Vocabulary)

    @Query("DELETE FROM vocabulary WHERE id = :id")
    suspend fun deleteVocabularyById(id: Long)

    @Query("""
        SELECT * FROM vocabulary 
        WHERE LOWER(TRIM(word)) = LOWER(TRIM(:word))
        AND (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'spanish')))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'english')))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'french')))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'german')))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'italian')))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'russian')))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'ukrainian')))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'polish')))) IN ('polish', 'pl'))
        ) 
        LIMIT 1
    """)
    suspend fun getVocabularyByWordAndLanguage(word: String, languageCode: String): Vocabulary?

    @Query("""
        SELECT * FROM vocabulary 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'spanish')))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'english')))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'french')))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'german')))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'italian')))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'russian')))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'ukrainian')))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'polish')))) IN ('polish', 'pl'))
        ) 
        AND IFNULL(interference_tag, '') NOT IN ('LANGUAGE_ISLAND', 'ACCELERATOR_NODE') 
        ORDER BY dateAdded DESC LIMIT 10
    """)
    suspend fun getRecentVocabularySync(languageCode: String): List<Vocabulary>

    // --- Mistakes ---
    @Query("""
        SELECT * FROM mistakes 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, 'spanish'))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, 'english'))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, 'french'))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, 'german'))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, 'italian'))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, 'russian'))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, 'ukrainian'))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, 'polish'))) IN ('polish', 'pl'))
        ) 
        ORDER BY timestamp DESC
    """)
    fun getAllMistakes(languageCode: String): Flow<List<Mistake>>

    @Query("""
        SELECT * FROM mistakes 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, 'spanish'))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, 'english'))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, 'french'))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, 'german'))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, 'italian'))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, 'russian'))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, 'ukrainian'))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, 'polish'))) IN ('polish', 'pl'))
        ) 
        ORDER BY timestamp DESC LIMIT 10
    """)
    suspend fun getRecentMistakesSync(languageCode: String): List<Mistake>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: Mistake): Long

    @Update
    suspend fun updateMistake(mistake: Mistake)

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun deleteMistakeById(id: Long)

    // --- Mnemonics ---
    @Query("SELECT * FROM mnemonics ORDER BY timestamp DESC")
    fun getAllMnemonics(): Flow<List<Mnemonic>>

    @Query("""
        SELECT * FROM mnemonics 
        WHERE (
            LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language, 'spanish'))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language, 'english'))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language, 'french'))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language, 'german'))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language, 'italian'))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language, 'russian'))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language, 'ukrainian'))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language, 'polish'))) IN ('polish', 'pl'))
        ) 
        ORDER BY timestamp DESC
    """)
    fun getMnemonicsForLanguage(languageCode: String): Flow<List<Mnemonic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMnemonic(mnemonic: Mnemonic): Long

    @Query("DELETE FROM mnemonics WHERE id = :id")
    suspend fun deleteMnemonicById(id: Long)

    // --- Stories ---
    @Query("SELECT * FROM stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<Story>>

    @Query("""
        SELECT * FROM stories 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR LOWER(TRIM(language)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'spanish')))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'english')))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'french')))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'german')))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'italian')))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'russian')))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'ukrainian')))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, IFNULL(language, 'polish')))) IN ('polish', 'pl'))
        ) 
        ORDER BY timestamp DESC
    """)
    fun getStoriesForLanguage(languageCode: String): Flow<List<Story>>

    @Query("SELECT * FROM stories WHERE id = :id LIMIT 1")
    suspend fun getStoryById(id: Long): Story?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story): Long

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStoryById(id: Long)

    // --- Dictionary Cache ---
    @Query("""
        SELECT * FROM dictionary_cache 
        WHERE (
            LOWER(TRIM(language_code)) = LOWER(TRIM(:languageCode))
            OR (LOWER(TRIM(:languageCode)) IN ('spanish', 'es') AND LOWER(TRIM(IFNULL(language_code, 'spanish'))) IN ('spanish', 'es', ''))
            OR (LOWER(TRIM(:languageCode)) IN ('english', 'en') AND LOWER(TRIM(IFNULL(language_code, 'english'))) IN ('english', 'en'))
            OR (LOWER(TRIM(:languageCode)) IN ('french', 'fr') AND LOWER(TRIM(IFNULL(language_code, 'french'))) IN ('french', 'fr'))
            OR (LOWER(TRIM(:languageCode)) IN ('german', 'de') AND LOWER(TRIM(IFNULL(language_code, 'german'))) IN ('german', 'de'))
            OR (LOWER(TRIM(:languageCode)) IN ('italian', 'it') AND LOWER(TRIM(IFNULL(language_code, 'italian'))) IN ('italian', 'it'))
            OR (LOWER(TRIM(:languageCode)) IN ('russian', 'ru') AND LOWER(TRIM(IFNULL(language_code, 'russian'))) IN ('russian', 'ru'))
            OR (LOWER(TRIM(:languageCode)) IN ('ukrainian', 'uk') AND LOWER(TRIM(IFNULL(language_code, 'ukrainian'))) IN ('ukrainian', 'uk'))
            OR (LOWER(TRIM(:languageCode)) IN ('polish', 'pl') AND LOWER(TRIM(IFNULL(language_code, 'polish'))) IN ('polish', 'pl'))
        ) 
        AND (normalized_query = :normalizedQuery OR translated_text = :normalizedQuery) 
        LIMIT 1
    """)
    suspend fun getDictionaryEntry(languageCode: String, normalizedQuery: String): DictionaryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDictionaryEntry(entry: DictionaryCacheEntity): Long

    @Query("SELECT * FROM dictionary_cache ORDER BY timestamp DESC LIMIT 20")
    fun getRecentDictionarySearches(): Flow<List<DictionaryCacheEntity>>

    @Query("DELETE FROM dictionary_cache WHERE id = :id")
    suspend fun deleteDictionaryEntryById(id: Long)

    @Query("DELETE FROM dictionary_cache WHERE id NOT IN (SELECT id FROM dictionary_cache ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneOldDictionaryEntries(limit: Int = 300)
}
