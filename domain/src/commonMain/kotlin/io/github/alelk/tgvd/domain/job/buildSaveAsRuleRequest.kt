package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.storage.OutputTarget

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
    val metadataTemplate = job.metadata.toTemplate(includeOverrides = includeMetadataTemplate)

    // Convert concrete paths back to templates by replacing variable parts with placeholders.
    val outputs = job.storagePlan.allTargets.map { target ->
        target.toOutputRule(job.source.videoId.value, includeStoragePolicy)
    }

    return CreateRuleRequest(
        workspaceId = workspaceId,
        name = "Auto: ${job.source.extractor.value} / ${match::class.simpleName}",
        match = match,
        metadataTemplate = metadataTemplate,
        downloadPolicy = DownloadPolicy(),
        outputs = outputs,
        enabled = enabled,
        priority = 0,
    )
}

private fun ResolvedMetadata.toTemplate(includeOverrides: Boolean): MetadataTemplate = when (this) {
    is ResolvedMetadata.MusicVideo -> MetadataTemplate.MusicVideo(
        artistOverride = artist.takeIf { includeOverrides },
    )
    is ResolvedMetadata.SeriesEpisode -> MetadataTemplate.SeriesEpisode(
        seriesNameOverride = seriesName.takeIf { includeOverrides },
    )
    is ResolvedMetadata.Other -> MetadataTemplate.Other()
}

private fun OutputTarget.toOutputRule(videoId: String, includeStoragePolicy: Boolean): OutputRule =
    OutputRule(
        pathTemplate = path.value.replace(videoId, "{videoId}"),
        format = format,
        maxQuality = maxQuality.takeIf { includeStoragePolicy },
        encodeSettings = encodeSettings.takeIf { includeStoragePolicy },
        embedThumbnail = includeStoragePolicy && embedThumbnail,
        embedMetadata = includeStoragePolicy && embedMetadata,
        embedSubtitles = includeStoragePolicy && embedSubtitles,
        normalizeAudio = includeStoragePolicy && normalizeAudio,
    )
