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
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.storage.OutputDefaults
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor

class PreviewUseCase(
    private val videoInfoExtractor: VideoInfoExtractor,
    private val videoInfoCache: VideoInfoCache,
    private val ruleMatchingService: RuleMatchingService,
    private val metadataResolver: MetadataResolver,
    private val llmPort: LlmPort?,
    private val txRunner: TransactionRunner,
) {
    /**
     * Compute a preview for the given [url].
     *
     * The read-only transaction wraps the DB access (cache + rule matching) only.
     * The LLM call is intentionally performed *outside* the transaction because it is
     * a long-running network request that must not hold a DB connection open.
     *
     * @param force when `true` — bypass the video-info cache and always call yt-dlp;
     *              the fresh result is then written back to the cache.
     */
    suspend operator fun invoke(
        url: String,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
        force: Boolean = false,
    ): Either<DomainError, PreviewResult> = either {
        // 1 & 2: VideoInfo + rule matching — within a single read-only transaction
        val (videoInfo, matchResult, cacheMiss) =
            txRunner.inRoTransaction {
                either {
                    val cached = if (force) null else videoInfoCache.get(url)
                    val info = cached ?: videoInfoExtractor.extract(url).bind()
                    val match = ruleMatchingService.findMatchingRule(info, workspaceId, overrides)
                    Triple(info, match, cached == null)
                }.bind()
            }

        // Write cache outside the read-only transaction
        if (cacheMiss) {
            txRunner.inRwTransaction { either<DomainError, Unit> { videoInfoCache.put(url, videoInfo) } }
        }

        // 3. Resolve metadata (rule + channel overrides → LLM → fallback)
        // LLM call is outside the transaction — it is a network request and must not hold a DB connection.
        val (metadata, source) = resolveMetadata(videoInfo, matchResult)

        // 4. Apply user overrides on top of resolved metadata
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
     * Applies user overrides on top of resolved metadata.
     * Override fields take the highest priority.
     * The sealed overrides type determines the target [ResolvedMetadata] category.
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
