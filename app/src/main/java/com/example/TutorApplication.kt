package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.repository.IAcceleratorRepository
import com.example.data.repository.IContentRepository
import com.example.data.repository.IDictionaryRepository
import com.example.data.repository.ILiveChatRepository
import com.example.data.repository.IMnemonicRepository
import com.example.data.repository.IShadowingRepository
import com.example.data.repository.IVocabularyRepository

class TutorApplication : Application() {
    companion object {
        lateinit var instance: TutorApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.data.api.NetworkMonitor.initialize(this)
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            android.util.Log.e("TutorApplication", "FATAL CRASH on thread ${thread.name}: ${e.message}", e)
            defaultHandler?.uncaughtException(thread, e)
        }
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    val liveChatRepository: ILiveChatRepository by lazy { com.example.data.repository.LiveChatRepository(database.tutorDao()) }
    val acceleratorRepository: IAcceleratorRepository by lazy { com.example.data.repository.AcceleratorRepository() }
    val dictionaryRepository: IDictionaryRepository by lazy { com.example.data.repository.DictionaryRepository(database.tutorDao()) }
    val contentRepository: IContentRepository by lazy { com.example.data.repository.ContentRepository(database.tutorDao()) }
    val vocabularyRepository: IVocabularyRepository by lazy { com.example.data.repository.VocabularyRepository(database.tutorDao()) }
    val mnemonicRepository: IMnemonicRepository by lazy { com.example.data.repository.MnemonicRepository(database.tutorDao()) }
    val shadowingRepository: IShadowingRepository by lazy { com.example.data.repository.ShadowingRepository(database.shadowingDao()) }
}
