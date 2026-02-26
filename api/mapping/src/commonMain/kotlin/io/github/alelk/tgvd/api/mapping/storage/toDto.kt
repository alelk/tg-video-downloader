package io.github.alelk.tgvd.api.mapping.storage

import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.OutputTemplateDto
import io.github.alelk.tgvd.api.contract.storage.PostProcessPolicyDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.StoragePolicyDto
import io.github.alelk.tgvd.domain.storage.*

fun DownloadPolicy.toDto(): DownloadPolicyDto =
    DownloadPolicyDto(
        maxQuality = maxQuality.name.lowercase(),
        preferredContainer = preferredContainer?.extension,
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )

fun OutputTemplate.toDto(): OutputTemplateDto =
    OutputTemplateDto(pathTemplate = pathTemplate, format = format.serialized)

fun StoragePolicy.toDto(): StoragePolicyDto =
    StoragePolicyDto(
        originalTemplate = originalTemplate,
        additionalOutputs = additionalOutputs.map { it.toDto() },
    )

fun PostProcessPolicy.toDto(): PostProcessPolicyDto =
    PostProcessPolicyDto(embedThumbnail, embedMetadata, normalizeAudio)

fun OutputTarget.toDto(): OutputTargetDto =
    OutputTargetDto(path = path.value, format = format.serialized)

fun StoragePlan.toDto(): StoragePlanDto =
    StoragePlanDto(original = original.toDto(), additional = additional.map { it.toDto() })

