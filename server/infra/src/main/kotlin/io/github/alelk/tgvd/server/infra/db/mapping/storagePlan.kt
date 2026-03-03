package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.server.infra.db.model.OutputTargetPm
import io.github.alelk.tgvd.server.infra.db.model.StoragePlanPm

internal fun StoragePlan.toPm(): StoragePlanPm =
    StoragePlanPm(original = original.toPm(), additional = additional.map { it.toPm() })

internal fun StoragePlanPm.toDomain(): StoragePlan =
    StoragePlan(original = original.toDomain(), additional = additional.map { it.toDomain() })

private fun OutputTarget.toPm(): OutputTargetPm =
    OutputTargetPm(
        path = path.value,
        format = format.serialized,
        maxQuality = maxQuality?.name?.lowercase(),
        encodeSettings = encodeSettings?.toPm(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

private fun OutputTargetPm.toDomain(): OutputTarget =
    OutputTarget(
        path = FilePath(path),
        format = OutputFormat.parse(format),
        maxQuality = maxQuality?.let { q ->
            DownloadPolicy.VideoQuality.entries.find { it.name.equals(q, ignoreCase = true) }
        },
        encodeSettings = encodeSettings?.toDomain(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

