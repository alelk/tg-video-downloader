package io.github.alelk.tgvd.api.mapping.preview

import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.mapping.common.toDto
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDto
import io.github.alelk.tgvd.api.mapping.video.toDto
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.preview.UserOverrides
import io.github.alelk.tgvd.domain.preview.VideoPreview
import kotlin.uuid.ExperimentalUuidApi

fun UserOverrides.toDto(): UserOverridesDto = when (this) {
    is UserOverrides.MusicVideo ->
        UserOverridesDto.MusicVideo(
            artist = artist,
            title = title,
            album = album,
        )
    is UserOverrides.SeriesEpisode ->
        UserOverridesDto.SeriesEpisode(
            seriesName = seriesName,
            season = season,
            episode = episode,
            title = title,
        )
    is UserOverrides.Other ->
        UserOverridesDto.Other(
            title = title,
        )
}

@OptIn(ExperimentalUuidApi::class)
fun VideoPreview.toDto(): PreviewResponseDto = PreviewResponseDto(
    source = source.toDto(),
    videoInfo = result.videoInfo.toDto(),
    matchedRule = result.matchedRule?.let { RuleSummaryDto(id = it.id.value.toString(), name = it.name) },
    metadataSource = result.metadataSource.toDto(),
    category = result.metadata.category.toDto(),
    metadata = result.metadata.toDto(),
    storagePlan = storagePlan.toDto(),
    appliedOverrides = appliedOverrides?.toDto(),
    previousDownloads = previousDownloads.map { it.toDownloadHistoryEntryDto() },
    defaultMediaSelection = defaultMediaSelection.toDto(),
)

/** A finished job as an entry of [PreviewResponseDto.previousDownloads]; `status` is the upper-case enum name. */
@OptIn(ExperimentalUuidApi::class)
fun Job.toDownloadHistoryEntryDto(): DownloadHistoryEntryDto = DownloadHistoryEntryDto(
    jobId = id.value.toString(),
    status = status.name,
    finishedAt = finishedAt?.toString(),
    maxQuality = storagePlan.original.maxQuality?.toDto(),
    formatSummary = storagePlan.original.format.extension,
)
