package io.github.alelk.tgvd.domain.system

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError

interface YtDlpService {
    suspend fun version(): Either<DomainError, YtDlpVersion>
    suspend fun update(): Either<DomainError, YtDlpVersion>
}
