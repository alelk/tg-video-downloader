package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.server.infra.db.model.DownloadPolicyPm

internal fun DownloadPolicy.toPm(): DownloadPolicyPm =
    DownloadPolicyPm(
        maxQuality = maxQuality.name.lowercase(),
        preferredContainer = preferredContainer?.extension,
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )

internal fun DownloadPolicyPm.toDomain(): DownloadPolicy =
    DownloadPolicy(
        maxQuality = DownloadPolicy.VideoQuality.entries
            .find { it.name.equals(maxQuality, ignoreCase = true) }
            ?: DownloadPolicy.VideoQuality.BEST,
        preferredContainer = preferredContainer?.let { MediaContainer.fromExtension(it) },
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )

