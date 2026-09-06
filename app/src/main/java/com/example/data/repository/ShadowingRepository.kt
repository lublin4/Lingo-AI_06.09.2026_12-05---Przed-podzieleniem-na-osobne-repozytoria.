package com.example.data.repository

import com.example.data.local.dao.ShadowingDao
import com.example.data.local.entities.ShadowingEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn

class ShadowingRepository(
    private val shadowingDao: ShadowingDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
) : IShadowingRepository {
    private val sharedPrefs by lazy {
        com.example.TutorApplication.instance.getSharedPreferences("shadowing_prefs", android.content.Context.MODE_PRIVATE)
    }

    override suspend fun saveCurrentMode(mode: String) = withContext(ioDispatcher) {
        sharedPrefs.edit().putString("current_mode", mode).apply()
    }

    override suspend fun getCurrentMode(): String = withContext(ioDispatcher) {
        sharedPrefs.getString("current_mode", "STANDARD") ?: "STANDARD"
    }

    override suspend fun saveCurrentMetadata(metadata: String) = withContext(ioDispatcher) {
        sharedPrefs.edit().putString("current_metadata", metadata).apply()
    }

    override suspend fun getCurrentMetadata(): String? = withContext(ioDispatcher) {
        sharedPrefs.getString("current_metadata", null)
    }

    override fun getAllSessions(): Flow<List<ShadowingEntity>> = shadowingDao.getAllSessions()
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    override suspend fun saveSession(
        phrase: String,
        translation: String,
        language: String,
        audioPath: String?,
        learningMode: String?,
        poeticContext: String?,
        metadata: String?
    ): Long {
        return withContext(ioDispatcher) {
            val duplicateCount = shadowingDao.countDuplicate(phrase.trim(), language)
            if (duplicateCount > 0) {
                return@withContext -1L
            }
            shadowingDao.insertSession(
                ShadowingEntity(
                    foreignPhrase = phrase.trim(),
                    nativeTranslation = translation,
                    targetLanguage = language,
                    audioPath = audioPath,
                    learningMode = learningMode,
                    poeticContext = poeticContext,
                    metadata = metadata
                )
            )
        }
    }

    override suspend fun deleteSession(id: Long) {
        withContext(ioDispatcher) {
            try {
                val session = shadowingDao.getSessionById(id)
                session?.audioPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore file deletion error and proceed to DB delete
            }
            shadowingDao.deleteSessionById(id)
        }
    }
}
