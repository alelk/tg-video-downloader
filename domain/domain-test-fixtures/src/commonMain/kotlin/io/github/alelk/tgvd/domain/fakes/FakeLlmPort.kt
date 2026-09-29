package io.github.alelk.tgvd.domain.fakes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.LlmSuggestion
import io.github.alelk.tgvd.domain.video.VideoInfo

/**
 * [LlmPort] that answers every video with [suggestion], or with [DomainError.LlmError] when it is `null`
 * (the default — an LLM with nothing to say, like an unconfigured one).
 */
class FakeLlmPort(var suggestion: LlmSuggestion? = null) : LlmPort {
    /** The videos asked about, in order. */
    val asked = mutableListOf<VideoInfo>()

    override suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion> {
        asked += video
        return suggestion?.right() ?: DomainError.LlmError("fake", "No suggestion").left()
    }
}
