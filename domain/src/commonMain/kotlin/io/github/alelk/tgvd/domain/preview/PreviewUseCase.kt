package io.github.alelk.tgvd.domain.preview

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.metadata.mergeTemplates
import io.github.alelk.tgvd.domain.rule.MatchResult
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.storage.OutputDefaults
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor

data class PreviewResult(
    val videoInfo: VideoInfo,
    val metadata: ResolvedMetadata,
    val metadataSource: MetadataSource,
    val matchedRule: Rule?,
    val outputs: List<OutputRule>,
)

class PreviewUseCase(
    private val videoInfoExtractor: VideoInfoExtractor,
    private val videoInfoCache: VideoInfoCache,
    private val ruleMatchingService: RuleMatchingService,
    private val metadataResolver: MetadataResolver,
    private val llmPort: LlmPort?,
) {
    suspend fun preview(
        url: String,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
    ): Either<DomainError, PreviewResult> = either {
        // 1. VideoInfo: кэш (PostgreSQL) или yt-dlp
        val videoInfo = videoInfoCache.get(url)
            ?: videoInfoExtractor.extract(url).bind().also { videoInfoCache.put(url, it) }

        // 2. Rule matching с учётом overrides и channel directory
        val matchResult = ruleMatchingService.findMatchingRule(videoInfo, workspaceId, overrides)

        // 3. Resolve metadata (rule + channel overrides → LLM → fallback)
        val (metadata, source) = resolveMetadata(videoInfo, matchResult)

        // 4. Apply user overrides поверх resolved metadata
        val finalMetadata = applyOverrides(metadata, overrides)

        // 5. Outputs
        val outputs = matchResult?.rule?.outputs ?: OutputDefaults.defaultFor(finalMetadata.category)

        PreviewResult(
            videoInfo = videoInfo,
            metadata = finalMetadata,
            metadataSource = source,
            matchedRule = matchResult?.rule,
            outputs = outputs,
        )
    }

    /**
     * Применяет user overrides поверх resolved metadata.
     * Override-поля имеют наивысший приоритет.
     * Тип sealed overrides определяет целевую категорию.
     */
    private fun applyOverrides(
        metadata: ResolvedMetadata,
        overrides: UserOverrides?,
    ): ResolvedMetadata {
        if (overrides == null) return metadata

        return when (overrides) {
            is UserOverrides.MusicVideo -> ResolvedMetadata.MusicVideo(
                artist = overrides.artist
                    ?: (metadata as? ResolvedMetadata.MusicVideo)?.artist
                    ?: "Unknown Artist",
                title = overrides.title ?: metadata.title,
                releaseDate = metadata.releaseDate,
                tags = metadata.tags,
                comment = metadata.comment,
            )
            is UserOverrides.SeriesEpisode -> ResolvedMetadata.SeriesEpisode(
                seriesName = overrides.seriesName
                    ?: (metadata as? ResolvedMetadata.SeriesEpisode)?.seriesName
                    ?: "Unknown Series",
                season = overrides.season ?: (metadata as? ResolvedMetadata.SeriesEpisode)?.season,
                episode = overrides.episode ?: (metadata as? ResolvedMetadata.SeriesEpisode)?.episode,
                title = overrides.title ?: metadata.title,
                releaseDate = metadata.releaseDate,
                tags = metadata.tags,
                comment = metadata.comment,
            )
            is UserOverrides.Other -> ResolvedMetadata.Other(
                title = overrides.title ?: metadata.title,
                releaseDate = metadata.releaseDate,
                tags = metadata.tags,
                comment = metadata.comment,
            )
        }
    }

    private suspend fun resolveMetadata(
        video: VideoInfo, matchResult: MatchResult?,
    ): Pair<ResolvedMetadata, MetadataSource> {
        return if (matchResult != null) {
            val effectiveTemplate = mergeTemplates(
                base = matchResult.rule.metadataTemplate,
                overlay = matchResult.channel?.metadataOverrides,
            )
            metadataResolver.resolve(video, effectiveTemplate) to MetadataSource.RULE
        } else {
            resolveFallback(video)
        }
    }

    private suspend fun resolveFallback(video: VideoInfo): Pair<ResolvedMetadata, MetadataSource> {
        if (llmPort != null) {
            val llmResult = llmPort.suggestMetadata(video)
            llmResult.onRight { suggestion ->
                return suggestion.metadata to MetadataSource.LLM
            }
        }
        val fallback = metadataResolver.resolve(video, MetadataTemplate.Other())
        return fallback to MetadataSource.FALLBACK
    }
}
