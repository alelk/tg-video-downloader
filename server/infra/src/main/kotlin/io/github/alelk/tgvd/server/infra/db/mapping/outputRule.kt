package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.server.infra.db.model.OutputRulePm

internal fun OutputRule.toPm(): OutputRulePm =
    OutputRulePm(
        pathTemplate = pathTemplate,
        format = format.serialized,
        maxQuality = maxQuality?.name?.lowercase(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

internal fun OutputRulePm.toDomain(): OutputRule =
    OutputRule(
        pathTemplate = pathTemplate,
        format = OutputFormat.parse(format),
        maxQuality = maxQuality?.let { q ->
            DownloadPolicy.VideoQuality.entries.find { it.name.equals(q, ignoreCase = true) }
        },
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )
