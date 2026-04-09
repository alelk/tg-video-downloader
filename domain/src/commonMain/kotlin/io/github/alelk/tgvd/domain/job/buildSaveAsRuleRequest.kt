package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputRule

/**
 * Builds a [CreateRuleRequest] from a completed job, suitable for "save as rule" functionality.
 *
 * The generated rule:
 * - Matches by the provided [match] condition (e.g. ChannelId)
 * - Uses metadata extracted from the job as a template (overrides with actual values)
 * - Reproduces the storage plan from the job as output path templates (using placeholders)
 */
fun buildSaveAsRuleRequest(
    workspaceId: WorkspaceId,
    match: RuleMatch,
    job: Job,
    includeMetadataTemplate: Boolean = true,
    includeStoragePolicy: Boolean = true,
    enabled: Boolean = true,
): CreateRuleRequest {
    val metadataTemplate = if (includeMetadataTemplate) {
        when (val meta = job.metadata) {
            is ResolvedMetadata.MusicVideo -> MetadataTemplate.MusicVideo(
                artistOverride = meta.artist,
                titlePattern = null, // keep dynamic from video
            )
            is ResolvedMetadata.SeriesEpisode -> MetadataTemplate.SeriesEpisode(
                seriesNameOverride = meta.seriesName,
                seasonPattern = null,
                episodePattern = null,
            )
            is ResolvedMetadata.Other -> MetadataTemplate.Other()
        }
    } else {
        when (job.metadata) {
            is ResolvedMetadata.MusicVideo -> MetadataTemplate.MusicVideo()
            is ResolvedMetadata.SeriesEpisode -> MetadataTemplate.SeriesEpisode()
            is ResolvedMetadata.Other -> MetadataTemplate.Other()
        }
    }

    val outputs = if (includeStoragePolicy) {
        // Convert concrete paths back to templates by replacing variable parts with placeholders
        job.storagePlan.allTargets.map { target ->
            OutputRule(
                pathTemplate = target.path.value
                    .replace(job.source.videoId.value, "{videoId}"),
                format = target.format,
                maxQuality = target.maxQuality,
                encodeSettings = target.encodeSettings,
                embedThumbnail = target.embedThumbnail,
                embedMetadata = target.embedMetadata,
                embedSubtitles = target.embedSubtitles,
                normalizeAudio = target.normalizeAudio,
            )
        }
    } else {
        job.storagePlan.allTargets.map { target ->
            OutputRule(
                pathTemplate = target.path.value
                    .replace(job.source.videoId.value, "{videoId}"),
                format = target.format,
            )
        }
    }

    return CreateRuleRequest(
        workspaceId = workspaceId,
        name = "Auto: ${job.source.extractor.value} / ${match::class.simpleName}",
        match = match,
        metadataTemplate = metadataTemplate,
        downloadPolicy = DownloadPolicy(),
        outputs = outputs.ifEmpty {
            listOf(
                OutputRule(
                    pathTemplate = job.storagePlan.original.path.value
                        .replace(job.source.videoId.value, "{videoId}"),
                    format = job.storagePlan.original.format,
                )
            )
        },
        enabled = enabled,
        priority = 0,
    )
}

