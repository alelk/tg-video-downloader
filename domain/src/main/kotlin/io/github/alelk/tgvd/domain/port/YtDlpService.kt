package io.github.alelk.tgvd.domain.port

import io.github.alelk.tgvd.domain.error.YtDlpError
import io.github.alelk.tgvd.domain.model.YtDlpVersion
import arrow.core.Either

interface YtDlpService {
    suspend fun getVersion(): Either<YtDlpError, YtDlpVersion>
    suspend fun checkForUpdates(): Either<YtDlpError, YtDlpVersion>
    suspend fun update(): Either<YtDlpError, YtDlpVersion>
}
