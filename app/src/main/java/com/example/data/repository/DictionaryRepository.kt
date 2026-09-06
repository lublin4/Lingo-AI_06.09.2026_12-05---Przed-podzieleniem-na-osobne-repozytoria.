package com.example.data.repository
import com.example.data.TutorDao
import com.example.data.DictionaryCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val singleThreadIo = Dispatchers.IO.limitedParallelism(1)

class DictionaryRepository(private val dao: TutorDao) : IDictionaryRepository {
    override val recentSearches: Flow<List<DictionaryCacheEntity>> = dao.getRecentDictionarySearches().catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    
    override suspend fun getDictionaryEntry(languageCode: String, normalizedQuery: String): DictionaryCacheEntity? = withContext(Dispatchers.IO) {
        try { dao.getDictionaryEntry(languageCode, normalizedQuery) } catch (e: Exception) { null }
    }
    override suspend fun insertDictionaryEntry(entry: DictionaryCacheEntity): Long = withContext(singleThreadIo) {
        try { dao.insertDictionaryEntry(entry) } catch (e: Exception) { -1L }
    }
    override suspend fun deleteDictionaryEntryById(id: Long) = withContext(singleThreadIo) {
        try { dao.deleteDictionaryEntryById(id) } catch (e: Exception) { }
    }
    override suspend fun pruneOldEntries(limit: Int) = withContext(singleThreadIo) {
        try { dao.pruneOldDictionaryEntries(limit) } catch (e: Exception) { }
    }
}