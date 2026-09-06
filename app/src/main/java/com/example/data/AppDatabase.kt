package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.entities.ShadowingEntity
import com.example.data.local.dao.ShadowingDao

@Database(
    entities = [
        UserGoal::class,
        Session::class,
        ChatMessage::class,
        Vocabulary::class,
        Mistake::class,
        Mnemonic::class,
        Story::class,
        DictionaryCacheEntity::class,
        ShadowingEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tutorDao(): TutorDao
    abstract fun shadowingDao(): ShadowingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "language_tutor_database"
                )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
