package com.example.data.repository
import com.example.data.TutorDao
import com.example.data.UserGoal
import com.example.data.Session
import com.example.data.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val singleThreadIo = Dispatchers.IO.limitedParallelism(1)

class LiveChatRepository(private val dao: TutorDao) : ILiveChatRepository {
    override val goal: Flow<UserGoal?> = dao.getGoal().catch { emit(null) }.flowOn(Dispatchers.IO)
    override val allSessions: Flow<List<Session>> = dao.getAllSessions().catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    
    override suspend fun getGoalSync(): UserGoal? = withContext(Dispatchers.IO) {
        try { dao.getGoalSync() } catch (e: Exception) { null }
    }
    override suspend fun insertGoal(goal: UserGoal) = withContext(singleThreadIo) {
        try { dao.insertGoal(goal) } catch (e: Exception) { }
    }
    override suspend fun getSessionById(id: Long): Session? = withContext(Dispatchers.IO) {
        try { dao.getSessionById(id) } catch (e: Exception) { null }
    }
    override suspend fun insertSession(session: Session): Long = withContext(singleThreadIo) {
        try { dao.insertSession(session) } catch (e: Exception) { -1L }
    }
    override suspend fun updateSession(session: Session) = withContext(singleThreadIo) {
        try { dao.updateSession(session) } catch (e: Exception) { }
    }
    override suspend fun deleteSessionById(id: Long) = withContext(singleThreadIo) {
        try {
            dao.deleteMessagesBySessionId(id)
            dao.deleteMistakesBySessionId(id)
            dao.deleteSessionById(id)
        } catch (e: Exception) { }
    }
    override fun getMessagesForSession(sessionId: Long, languageCode: String): Flow<List<ChatMessage>> =
        dao.getMessagesForSession(sessionId).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override suspend fun getMessagesForSessionSync(sessionId: Long, languageCode: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        try { dao.getMessagesForSessionSync(sessionId) } catch (e: Exception) { emptyList() }
    }
    override suspend fun insertMessage(message: ChatMessage): Long = withContext(singleThreadIo) {
        val lang = message.language_code.ifBlank { "Spanish" }
        val normalized = message.copy(language_code = lang)
        try { dao.insertMessage(normalized) } catch (e: Exception) { -1L }
    }
    override suspend fun getRecentSessionsSync(): List<Session> = withContext(Dispatchers.IO) {
        try { dao.getRecentSessionsSync() } catch (e: Exception) { emptyList() }
    }
}