package io.github.alelk.tgvd.domain.metadata

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.video.VideoInfo

/**
 * Port: metadata suggestions from an LLM. Optional — a deployment without an LLM is valid and gets an
 * adapter that always answers [DomainError.LlmError] (never a nullable port).
 */
interface LlmPort {
    suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion>
}
