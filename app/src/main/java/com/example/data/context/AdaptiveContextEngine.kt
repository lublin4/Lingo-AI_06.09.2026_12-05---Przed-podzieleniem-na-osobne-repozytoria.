package com.example.data.context

import com.example.data.TutorDao
import com.example.data.UserGoal
import com.example.data.repository.IVocabularyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserProfileRepository(private val dao: TutorDao) {
    suspend fun getUserProfile(): UserGoal? = withContext(Dispatchers.IO) {
        try {
            dao.getGoalSync()
        } catch (e: Exception) {
            null
        }
    }
}

class AdaptiveContextEngine(
    private val userProfileRepository: UserProfileRepository,
    private val vocabularyRepository: IVocabularyRepository
) {
    suspend fun buildAdaptivePromptSegment(targetLanguage: String): String = withContext(Dispatchers.IO) {
        val profile = userProfileRepository.getUserProfile() ?: UserGoal()
        val recentMistakes = vocabularyRepository.getRecentMistakesSync(targetLanguage).take(3)
        val lowRetentionVocabs = vocabularyRepository.getRecentVocabularySync(targetLanguage).take(3)

        val builder = StringBuilder()
        builder.append("=== SYSTEM SENSORY CZASOWY: ADAPTACYJNE PERSONALIZOWANE KOTWICE ===\n")
        builder.append("Dostosuj generowany segment pod profil użytkownika:\n")
        builder.append("- Język docelowy: $targetLanguage (Poziom CEFR: ${profile.cefrLevel})\n")
        if (profile.focusArea.isNotBlank()) {
            builder.append("- Główny obszar skupienia: ${profile.focusArea}\n")
        }
        if (profile.studyPlan.isNotBlank()) {
            builder.append("- Cel/Plan nauki: ${profile.studyPlan}\n")
        }

        if (recentMistakes.isNotEmpty()) {
            builder.append("- Ostatnio popełniane błędy użytkownika (zintegruj ich poprawne formy lub ich kontekst, aby ułatwić korektę): ")
            builder.append(recentMistakes.joinToString("; ") { "'${it.correctedSentence}' (Błąd: '${it.originalSentence}')" })
            builder.append("\n")
        }

        if (lowRetentionVocabs.isNotEmpty()) {
            builder.append("- Słówka o niskim wskaźniku zapamiętania lub niedawno dodane (spróbuj wpleść co najmniej jedno naturalnie do tekstu, aby wzmocnić zapamiętanie): ")
            builder.append(lowRetentionVocabs.joinToString(", ") { "${it.word} (${it.translation})" })
            builder.append("\n")
        }
        builder.append("Użytkownik powinien odczuć, że materiał nawiązuje bezpośrednio do jego profilu lub utrwala błędy/słownictwo. Dodaj informację o zakotwiczeniu (w sekcji metadata).\n")
        builder.toString()
    }
}
