package io.github.alelk.tgvd.api.mapping.storage

import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.OutputTemplateDto
import io.github.alelk.tgvd.api.contract.storage.PostProcessPolicyDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.StoragePolicyDto
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.storage.*

fun DownloadPolicyDto.toDomain(): DownloadPolicy =
    DownloadPolicy(
        maxQuality = DownloadPolicy.VideoQuality.entries
            .find { it.name.equals(maxQuality, ignoreCase = true) }
            ?: DownloadPolicy.VideoQuality.BEST,
        preferredContainer = preferredContainer?.let { MediaContainer.fromExtension(it) },
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )

fun OutputTemplateDto.toDomain(): OutputTemplate =
    OutputTemplate(pathTemplate = pathTemplate, format = OutputFormat.parse(format))

fun StoragePolicyDto.toDomain(): StoragePolicy =
    StoragePolicy(
        originalTemplate = originalTemplate,
        additionalOutputs = additionalOutputs.map { it.toDomain() },
    )

fun PostProcessPolicyDto.toDomain(): PostProcessPolicy =
    PostProcessPolicy(embedThumbnail, embedMetadata, normalizeAudio)

fun OutputTargetDto.toDomain(): OutputTarget =
    OutputTarget(path = FilePath(path), format = OutputFormat.parse(format))

fun StoragePlanDto.toDomain(): StoragePlan =
    StoragePlan(original = original.toDomain(), additional = additional.map { it.toDomain() })

