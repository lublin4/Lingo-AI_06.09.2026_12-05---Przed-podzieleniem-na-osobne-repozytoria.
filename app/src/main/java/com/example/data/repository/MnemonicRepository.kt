package com.example.data.repository
import com.example.data.TutorDao
import com.example.data.Mnemonic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val singleThreadIo = Dispatchers.IO.limitedParallelism(1)

class MnemonicRepository(private val dao: TutorDao) : IMnemonicRepository {
    override val allMnemonics: Flow<List<Mnemonic>> = dao.getAllMnemonics().catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override fun getMnemonicsForLanguage(languageCode: String): Flow<List<Mnemonic>> =
        dao.getMnemonicsForLanguage(languageCode).catch { emit(emptyList()) }.flowOn(Dispatchers.IO)
    override suspend fun insertMnemonic(mnemonic: Mnemonic): Long = withContext(singleThreadIo) {
        try { dao.insertMnemonic(mnemonic) } catch (e: Exception) { -1L }
    }
    override suspend fun deleteMnemonicById(id: Long) = withContext(singleThreadIo) {
        try { dao.deleteMnemonicById(id) } catch (e: Exception) { }
    }
}