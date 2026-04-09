package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.common.Category

data class LlmSuggestion(
    val category: Category,
    val metadata: ResolvedMetadata,
    val confidence: Double,
)