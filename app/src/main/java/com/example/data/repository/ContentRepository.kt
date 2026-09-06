package com.example.data.repository
import com.example.data.TutorDao
import com.example.data.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val singleThreadIo = Dispatchers.IO.limitedParallelism(1)

class ContentRepository(private val dao: TutorDao) : IContentRepository {
    override suspend fun getStoryById(id: Long): Story? = withContext(Dispatchers.IO) { try { dao.getStoryById(id) } catch(e: Exception) { null } }
    override val allStories: Flow<List<Story>> = dao.getAllStories().catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override fun getStoriesForLanguage(languageCode: String): Flow<List<Story>> =
        dao.getStoriesForLanguage(languageCode).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    
    override suspend fun insertStory(story: Story): Long = withContext(singleThreadIo) {
        val lang = story.language_code.ifBlank { story.language }.ifBlank { "Spanish" }
        val normalizedStory = story.copy(language_code = lang, language = lang)
        try { 
            dao.insertStory(normalizedStory) 
        } catch (e: Exception) { 
            android.util.Log.e("ContentRepository", "Error inserting story", e)
            -1L 
        }
    }
    override suspend fun deleteStoryById(id: Long) = withContext(singleThreadIo) {
        try { dao.deleteStoryById(id) } catch (e: Exception) { }
    }
}