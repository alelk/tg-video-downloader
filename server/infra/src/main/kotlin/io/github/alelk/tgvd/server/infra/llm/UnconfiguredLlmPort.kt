package io.github.alelk.tgvd.server.infra.llm

import arrow.core.Either
import arrow.core.left
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.LlmSuggestion
import io.github.alelk.tgvd.domain.video.VideoInfo

/**
 * The [LlmPort] of a deployment without an LLM — a valid configuration, not an error state. Every
 * suggestion is refused, so metadata falls back to the rule-less resolver exactly as without a port.
 */
object UnconfiguredLlmPort : LlmPort {
    override suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion> =
        DomainError.LlmError(provider = "none", message = "LLM is not configured").left()
}
