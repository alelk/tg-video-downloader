package io.github.alelk.tgvd.api.mapping.system

import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.api.contract.system.YtDlpUpdateResponseDto
import io.github.alelk.tgvd.domain.system.YtDlpStatus
import io.github.alelk.tgvd.domain.system.YtDlpVersion

fun YtDlpStatus.toDto(): YtDlpStatusDto = YtDlpStatusDto(
    currentVersion = current.version,
    latestVersion = latest?.version,
    isUpdateAvailable = isUpdateAvailable,
    lastCheckedAt = checkedAt?.toString(),
)

/** The response of `POST /system/yt-dlp/update` for the [installed][this] version. */
fun YtDlpVersion.toUpdateResponseDto(): YtDlpUpdateResponseDto = YtDlpUpdateResponseDto(
    status = "UPDATED",
    message = "Updated to version $version",
)
