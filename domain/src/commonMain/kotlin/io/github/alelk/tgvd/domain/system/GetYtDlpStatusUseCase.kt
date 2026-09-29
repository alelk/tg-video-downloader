package io.github.alelk.tgvd.domain.system

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The installed yt-dlp and the latest release.
 *
 * @param latest `null` when the latest release could not be checked.
 * @param checkedAt when [latest] was checked; `null` together with [latest].
 */
data class YtDlpStatus(
    val current: YtDlpVersion,
    val latest: YtDlpVersion?,
    val isUpdateAvailable: Boolean,
    val checkedAt: Instant?,
)

/**
 * The yt-dlp status. The installed version is required; the latest release is best effort — a
 * GitHub outage or rate limit leaves it unknown instead of failing the status.
 */
class GetYtDlpStatusUseCase(private val ytDlpService: YtDlpService, private val clock: Clock) {
    suspend operator fun invoke(): Either<DomainError, YtDlpStatus> = either {
        val current = ytDlpService.version().bind()
        val latest = ytDlpService.latestVersion().getOrNull()
        YtDlpStatus(
            current = current,
            latest = latest,
            isUpdateAvailable = latest?.isNewerThan(current) ?: false,
            checkedAt = latest?.let { clock.now() },
        )
    }
}
