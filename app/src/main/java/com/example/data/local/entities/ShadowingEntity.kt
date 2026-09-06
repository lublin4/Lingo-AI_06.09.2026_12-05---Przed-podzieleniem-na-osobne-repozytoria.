package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(
    tableName = "shadowing_sessions",
    indices = [
        androidx.room.Index(value = ["targetLanguage", "createdAt"])
    ]
)
data class ShadowingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foreignPhrase: String,
    val nativeTranslation: String,
    val audioPath: String? = null,
    val userScore: Float = 0.0f,
    val targetLanguage: String,
    val createdAt: Long = System.currentTimeMillis(),
    val learningMode: String? = "STANDARD",
    val poeticContext: String? = null,
    val metadata: String? = null
)
