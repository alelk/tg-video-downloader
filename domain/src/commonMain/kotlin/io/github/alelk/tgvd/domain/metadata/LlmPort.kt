package io.github.alelk.tgvd.domain.metadata

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.video.VideoInfo

interface LlmPort {
    suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion>
}

data class LlmSuggestion(
    val category: Category,
    val metadata: ResolvedMetadata,
    val confidence: Double,
)
