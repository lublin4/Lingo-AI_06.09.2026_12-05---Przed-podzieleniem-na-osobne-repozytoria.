package com.example.data.context

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ContrastItem(
    val word: String,
    val translation: String,
    val relationType: String // "OPPOSITE", "GRADATION", "COLLOCATION", "CAUSE_EFFECT", "CATEGORY"
)

@JsonClass(generateAdapter = true)
data class SemanticContrastResponse(
    val originalWord: String,
    val opposites: List<ContrastItem> = emptyList(),
    val collocations: List<ContrastItem> = emptyList(),
    val hierarchies: List<ContrastItem> = emptyList()
)

object ContrastEngine {
    // Auxiliary helper for Contrast Layer operations
}
