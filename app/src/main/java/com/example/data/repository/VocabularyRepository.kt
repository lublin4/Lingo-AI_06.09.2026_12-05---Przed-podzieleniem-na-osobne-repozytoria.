package com.example.data.repository
import com.example.data.TutorDao
import com.example.data.Vocabulary
import com.example.data.Mistake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val singleThreadIo = Dispatchers.IO.limitedParallelism(1)

class VocabularyRepository(private val dao: TutorDao) : IVocabularyRepository {
    override fun getAllVocabulary(languageCode: String): Flow<List<Vocabulary>> = 
        dao.getAllVocabulary(languageCode).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override fun getLanguageIslands(languageCode: String): Flow<List<Vocabulary>> = 
        dao.getLanguageIslands(languageCode).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override fun getVocabularyForLanguage(languageCode: String): Flow<List<Vocabulary>> = 
        dao.getVocabularyForLanguage(languageCode).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override fun getAllMistakes(languageCode: String): Flow<List<Mistake>> = 
        dao.getAllMistakes(languageCode).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
        
    override suspend fun getRecentVocabularySync(languageCode: String): List<Vocabulary> = withContext(Dispatchers.IO) {
        try { dao.getRecentVocabularySync(languageCode) } catch (e: Exception) { emptyList() }
    }
    override suspend fun getVocabularyByWordAndLanguage(word: String, languageCode: String): Vocabulary? = withContext(Dispatchers.IO) {
        val cleanWord = word.trim()
        try { dao.getVocabularyByWordAndLanguage(cleanWord, languageCode) } catch (e: Exception) { null }
    }

    override suspend fun insertVocabularyWithDuplicateCheck(vocab: Vocabulary): Long = withContext(singleThreadIo) {
        val trimmedWord = vocab.word.trim()
        val normalizedWord = trimmedWord.lowercase()
        if (normalizedWord.isBlank()) return@withContext -1L
        
        val lang = vocab.language.ifBlank { vocab.language_code }.ifBlank { "Spanish" }
        val normalizedVocab = vocab.copy(
            word = trimmedWord,
            language = lang,
            language_code = lang
        )

        val existing = try {
            dao.getVocabularyByWordAndLanguage(normalizedWord, lang)
        } catch (e: Exception) {
            null
        }
        
        if (existing != null) {
            return@withContext -1L
        }
        
        try { dao.insertVocabularyTransaction(normalizedVocab) } catch (e: Exception) { -1L }
    }

    override suspend fun insertVocabulary(vocab: Vocabulary): Long {
        return insertVocabularyWithDuplicateCheck(vocab)
    }

    override suspend fun updateVocabulary(vocab: Vocabulary) = withContext(singleThreadIo) {
        val lang = vocab.language.ifBlank { vocab.language_code }
        val normalized = vocab.copy(language = lang, language_code = lang)
        try { dao.updateVocabulary(normalized) } catch (e: Exception) { }
    }
    override suspend fun deleteVocabularyById(id: Long) = withContext(singleThreadIo) {
        try { dao.deleteVocabularyById(id) } catch (e: Exception) { }
    }
    override suspend fun getRecentMistakesSync(languageCode: String): List<Mistake> = withContext(Dispatchers.IO) {
        try { dao.getRecentMistakesSync(languageCode) } catch (e: Exception) { emptyList() }
    }
    override suspend fun insertMistake(mistake: Mistake): Long = withContext(singleThreadIo) {
        // AGENTS.md rule: if difference is only punctuation/casing, do not store mistake
        val cleanOriginal = mistake.originalSentence.replace(Regex("[?.,!'\"\\s¡¿;:—-]"), "").lowercase()
        val cleanCorrected = mistake.correctedSentence.replace(Regex("[?.,!'\"\\s¡¿;:—-]"), "").lowercase()
        if (cleanOriginal == cleanCorrected || cleanCorrected.isBlank()) {
            return@withContext -1L
        }

        val lang = mistake.language_code.ifBlank { "Spanish" }
        val normalizedMistake = mistake.copy(language_code = lang)
        try { dao.insertMistake(normalizedMistake) } catch (e: Exception) { -1L }
    }
    override suspend fun updateMistake(mistake: Mistake) = withContext(singleThreadIo) {
        try { dao.updateMistake(mistake) } catch (e: Exception) { }
    }
    override suspend fun deleteMistakeById(id: Long) = withContext(singleThreadIo) {
        try { dao.deleteMistakeById(id) } catch (e: Exception) { }
    }
}