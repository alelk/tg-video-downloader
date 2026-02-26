package io.github.alelk.tgvd.domain.video

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError

interface VideoInfoExtractor {
    suspend fun extract(url: String): Either<DomainError, VideoInfo>
}
