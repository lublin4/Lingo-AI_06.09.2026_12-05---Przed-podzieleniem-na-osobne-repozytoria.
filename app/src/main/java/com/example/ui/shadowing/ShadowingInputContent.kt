package com.example.ui.shadowing

sealed class ShadowingSource {
    data class StoryParagraph(val paragraphText: String, val storyTitle: String, val storyId: String = "") : ShadowingSource()
    data class LiveChatMessage(val messageText: String, val contextTopic: String, val speakerName: String = "Tutor AI") : ShadowingSource()
    data class VocabularyIsland(val phrase: String, val translation: String, val category: String = "General") : ShadowingSource()
    data class YouTubeUrl(val videoUrl: String, val videoTitle: String? = null, val rawTranscriptHint: String? = null) : ShadowingSource()
    data class CustomPrompt(val userPrompt: String, val contextDomain: String = "General Conversation") : ShadowingSource()
    data class AiGenerated(val topic: String) : ShadowingSource()
}

data class ShadowingInputContent(
    val source: ShadowingSource,
    val targetLanguage: String = "English",
    val userLevel: String = "B2"
)
