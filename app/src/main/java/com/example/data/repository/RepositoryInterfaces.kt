package com.example.data.repository

import com.example.data.ChatMessage
import com.example.data.DictionaryCacheEntity
import com.example.data.Mistake
import com.example.data.Mnemonic
import com.example.data.Session
import com.example.data.Story
import com.example.data.UserGoal
import com.example.data.Vocabulary
import com.example.data.api.TutorAiService
import com.example.data.local.entities.ShadowingEntity
import kotlinx.coroutines.flow.Flow

interface IBaseRepository<T, ID> {
    fun getAll(): Flow<List<T>>
    suspend fun getById(id: ID): T?
    suspend fun insert(entity: T): Long
    suspend fun update(entity: T)
    suspend fun deleteById(id: ID)
}

interface IVocabularyRepository {
    fun getAllVocabulary(languageCode: String): Flow<List<Vocabulary>>
    fun getLanguageIslands(languageCode: String): Flow<List<Vocabulary>>
    fun getVocabularyForLanguage(languageCode: String): Flow<List<Vocabulary>>
    fun getAllMistakes(languageCode: String): Flow<List<Mistake>>
        
    suspend fun getRecentVocabularySync(languageCode: String): List<Vocabulary>
    suspend fun getVocabularyByWordAndLanguage(word: String, languageCode: String): Vocabulary?
    suspend fun insertVocabulary(vocab: Vocabulary): Long
    suspend fun insertVocabularyWithDuplicateCheck(vocab: Vocabulary): Long
    suspend fun updateVocabulary(vocab: Vocabulary)
    suspend fun deleteVocabularyById(id: Long)
    
    suspend fun getRecentMistakesSync(languageCode: String): List<Mistake>
    suspend fun insertMistake(mistake: Mistake): Long
    suspend fun updateMistake(mistake: Mistake)
    suspend fun deleteMistakeById(id: Long)
}

interface IShadowingRepository {
    suspend fun saveCurrentMode(mode: String)
    suspend fun getCurrentMode(): String
    suspend fun saveCurrentMetadata(metadata: String)
    suspend fun getCurrentMetadata(): String?
    fun getAllSessions(): Flow<List<ShadowingEntity>>
    suspend fun saveSession(
        phrase: String,
        translation: String,
        language: String,
        audioPath: String? = null,
        learningMode: String? = "STANDARD",
        poeticContext: String? = null,
        metadata: String? = null
    ): Long
    suspend fun deleteSession(id: Long)
}

interface ILiveChatRepository {
    val goal: Flow<UserGoal?>
    val allSessions: Flow<List<Session>>
    suspend fun getGoalSync(): UserGoal?
    suspend fun insertGoal(goal: UserGoal)
    suspend fun getSessionById(id: Long): Session?
    suspend fun insertSession(session: Session): Long
    suspend fun updateSession(session: Session)
    suspend fun deleteSessionById(id: Long)
    fun getMessagesForSession(sessionId: Long, languageCode: String): Flow<List<ChatMessage>>
    suspend fun getMessagesForSessionSync(sessionId: Long, languageCode: String): List<ChatMessage>
    suspend fun insertMessage(message: ChatMessage): Long
    suspend fun getRecentSessionsSync(): List<Session>
}

interface IAcceleratorRepository {
    suspend fun generateAcceleratorData(messageText: String, language: String): List<TutorAiService.BuzanNode>
}

interface IContentRepository {
    val allStories: Flow<List<Story>>
    fun getStoriesForLanguage(languageCode: String): Flow<List<Story>> = allStories
    suspend fun getStoryById(id: Long): Story?
    suspend fun insertStory(story: Story): Long
    suspend fun deleteStoryById(id: Long)
}

interface IDictionaryRepository {
    val recentSearches: Flow<List<DictionaryCacheEntity>>
    suspend fun getDictionaryEntry(languageCode: String, normalizedQuery: String): DictionaryCacheEntity?
    suspend fun insertDictionaryEntry(entry: DictionaryCacheEntity): Long
    suspend fun deleteDictionaryEntryById(id: Long)
    suspend fun pruneOldEntries(limit: Int = 300) {}
}

interface IMnemonicRepository {
    val allMnemonics: Flow<List<Mnemonic>>
    fun getMnemonicsForLanguage(languageCode: String): Flow<List<Mnemonic>> = allMnemonics
    suspend fun insertMnemonic(mnemonic: Mnemonic): Long
    suspend fun deleteMnemonicById(id: Long)
}
