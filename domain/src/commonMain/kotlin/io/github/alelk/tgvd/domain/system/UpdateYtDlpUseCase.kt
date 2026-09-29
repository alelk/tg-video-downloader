package io.github.alelk.tgvd.domain.system

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError

/**
 * Updates the yt-dlp binary to the latest release, unless the administrator disabled updates
 * ([DomainError.YtDlpUpdateDisabled]).
 *
 * @return the version installed after the update.
 */
class UpdateYtDlpUseCase(private val ytDlpService: YtDlpService, private val settingsStore: SystemSettingsStore) {
    suspend operator fun invoke(): Either<DomainError, YtDlpVersion> = either {
        ensure(settingsStore.isYtDlpUpdateAllowed()) { DomainError.YtDlpUpdateDisabled() }
        ytDlpService.update().bind()
    }
}
