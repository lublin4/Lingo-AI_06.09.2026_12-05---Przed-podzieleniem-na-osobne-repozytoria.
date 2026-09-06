package com.example.data

import androidx.compose.runtime.Immutable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "user_goals")
data class UserGoal(
    @PrimaryKey val id: Int = 1, // Only one goal profile exists
    val targetLanguage: String = "Spanish",
    val nativeLanguage: String = "English",
    val cefrLevel: String = "A2", // A1, A2, B1, B2, C1, C2
    val streak: Int = 0,
    val lastActiveTimestamp: Long = 0,
    val dailyGoalMinutes: Int = 15,
    val focusArea: String = "General Speaking",
    val studyPlan: String = "Practice daily dialogues for 15 minutes. Focus on grammatical coherence and fluid speech."
)

@Immutable
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, // e.g., "Ordering Tapas in Madrid"
    val scenarioKey: String, // "restaurant", "travel", "business", etc.
    val language: String,
    val cefrLevel: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val fluencyScore: Int = 0, // 0 to 100
    val feedbackStrength: String = "",
    val feedbackImprovement: String = "",
    val feedbackVocabulary: String = "", // comma-separated or text
    val feedbackGrammar: String = "",
    val feedbackStudyPlan: String = "",
    val stressLevel: Int = 0 // 0 = Safe, 1 = Real-life (Moderate), 2 = High-stress (Time pressure/Noise)
)

@Immutable
@Entity(
    tableName = "chat_messages",
    indices = [
        androidx.room.Index(value = ["sessionId", "language_code"]),
        androidx.room.Index(value = ["sessionId", "timestamp"])
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sender: String, // "user" or "tutor"
    val originalText: String,
    val translatedText: String? = null,
    val isCorrected: Boolean = false,
    val correctedText: String? = null,
    val correctionExplanation: String? = null,
    val speechDurationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val pronunciationScore: Int = 0,
    val pronunciationFeedback: String? = null,
    val phoneticGuide: String? = null,
    val language_code: String = "Spanish"
)

@Immutable
@Entity(
    tableName = "vocabulary",
    indices = [
        androidx.room.Index(value = ["language_code", "word"]),
        androidx.room.Index(value = ["language_code", "nextReviewTimestamp"]),
        androidx.room.Index(value = ["language"])
    ]
)
data class Vocabulary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val translation: String,
    val language: String,
    val sentenceContext: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val masteryLevel: Int = 0, // 0 (New), 1 (Learning), 2 (Mastered)
    val nextReviewTimestamp: Long = System.currentTimeMillis(),
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val polishWordTranslation: String = "",
    val polishContextTranslation: String = "",
    val language_code: String = "Spanish",
    val last_response_latency_ms: Long = 0,
    val interference_tag: String = ""
)

@Immutable
@Entity(
    tableName = "mistakes",
    indices = [
        androidx.room.Index(value = ["sessionId"]),
        androidx.room.Index(value = ["language_code", "timestamp"]),
        androidx.room.Index(value = ["language_code", "nextReviewTimestamp"])
    ]
)
data class Mistake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val originalSentence: String,
    val correctedSentence: String,
    val explanation: String,
    val type: String, // "Grammar", "Word Choice", "Pronunciation"
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val nextReviewTimestamp: Long = System.currentTimeMillis(),
    val language_code: String = "Spanish",
    val last_response_latency_ms: Long = 0,
    val interference_tag: String = ""
)

data class SrsCard(
    val id: Long,
    val isVocabulary: Boolean,
    val front: String,
    val back: String,
    val title: String,
    val hint: String,
    val language: String,
    val masteryLevel: Int,
    val nextReviewTimestamp: Long,
    val rawObject: Any,
    val language_code: String = "Spanish",
    val last_response_latency_ms: Long = 0,
    val interference_tag: String = ""
)

@Immutable
@Entity(
    tableName = "mnemonics",
    indices = [
        androidx.room.Index(value = ["language", "timestamp"])
    ]
)
data class Mnemonic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordOrPhrase: String,
    val association: String, // Polish mnemonic description
    val language: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "stories",
    indices = [
        androidx.room.Index(value = ["language_code", "timestamp"]),
        androidx.room.Index(value = ["language", "timestamp"])
    ]
)
data class Story(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val translation: String = "",
    val language: String,
    val difficulty: String,
    val keyVocabulary: String = "",
    val grammarExplanation: String = "",
    val comprehensionQuestions: String = "",
    val scenariosJson: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val language_code: String = "Spanish"
)

@Immutable
@Entity(
    tableName = "dictionary_cache",
    indices = [androidx.room.Index(value = ["language_code", "normalized_query"], unique = true)]
)
data class DictionaryCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language_code: String,
    val normalized_query: String,
    val translated_text: String = "",
    val phonetic: String = "",
    val part_of_speech: String = "",
    val definition_pl: String = "",
    val example_target: String = "",
    val example_pl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
