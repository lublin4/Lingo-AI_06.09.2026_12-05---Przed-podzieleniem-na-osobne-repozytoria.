package com.example.data.repository
 
import com.example.data.api.TutorAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class AcceleratorRepository : IAcceleratorRepository {
    // Thread-safe in-memory cache for fast repeated responses
    private val memoryCache = ConcurrentHashMap<String, List<TutorAiService.BuzanNode>>()

    override suspend fun generateAcceleratorData(messageText: String, language: String): List<TutorAiService.BuzanNode> = withContext(Dispatchers.IO) {
        val trimmed = messageText.trim()
        val cacheKey = "${language.lowercase().trim()}_$trimmed"
        memoryCache[cacheKey]?.let { cached ->
            return@withContext cached
        }

        try {
            val response = withTimeoutOrNull(8000L) {
                TutorAiService.generateCommunicationElements(
                    trimmed,
                    language,
                    "B1"
                )
            }
            val result = response?.elements ?: listOf(
                TutorAiService.BuzanNode(
                    core_pattern = "Błąd",
                    core_translationPL = "Przekroczono limit czasu lub brak sieci",
                    radial_branches = emptyList(),
                    buzan_anchor = "error",
                    explanation = "Spróbuj ponownie"
                )
            )
            if (response?.elements != null && response.elements.isNotEmpty()) {
                if (memoryCache.size > 100) {
                    memoryCache.clear()
                }
                memoryCache[cacheKey] = result
            }
            result
        } catch (e: Exception) {
            listOf(
                TutorAiService.BuzanNode(
                    core_pattern = "Błąd",
                    core_translationPL = "Błąd połączenia: ${e.localizedMessage ?: "brak sieci"}",
                    radial_branches = emptyList(),
                    buzan_anchor = "error",
                    explanation = "Spróbuj ponownie"
                )
            )
        }
    }
}
