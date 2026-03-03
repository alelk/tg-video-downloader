package io.github.alelk.tgvd.domain.preview

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.storage.OutputDefaults
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.video.VideoInfo
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
    private val ruleMatchingService: RuleMatchingService,
    private val metadataResolver: MetadataResolver,
    private val llmPort: LlmPort?,
) {
    suspend fun preview(url: String, workspaceId: WorkspaceId): Either<DomainError, PreviewResult> {
        return videoInfoExtractor.extract(url).map { videoInfo ->
            val matchedRule = ruleMatchingService.findMatchingRule(videoInfo, workspaceId)

            val (metadata, source) = if (matchedRule != null) {
                metadataResolver.resolve(videoInfo, matchedRule.metadataTemplate) to MetadataSource.RULE
            } else {
                resolveFallback(videoInfo)
            }

            val outputs = matchedRule?.outputs
                ?: OutputDefaults.defaultFor(metadata.category)

            PreviewResult(
                videoInfo = videoInfo,
                metadata = metadata,
                metadataSource = source,
                matchedRule = matchedRule,
                outputs = outputs,
            )
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
