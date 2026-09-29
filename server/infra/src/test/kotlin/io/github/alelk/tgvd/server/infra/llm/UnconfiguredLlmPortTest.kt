package io.github.alelk.tgvd.server.infra.llm

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.category
import io.github.alelk.tgvd.domain.preview.PreviewResult
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.storage.OutputDefaults
import io.github.alelk.tgvd.domain.tx.RoTransactionScope
import io.github.alelk.tgvd.domain.tx.RwTransactionScope
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * G8: a deployment without an LLM gets [UnconfiguredLlmPort] instead of a `null` port. The preview must
 * come out exactly as it did with `llmPort = null`: no rule → metadata from the empty template, source
 * FALLBACK, the default outputs of its category.
 *
 * Self-contained on purpose: `domain-test-fixtures` is not on this module's test classpath (its
 * file-level classes share JVM names with `domain`'s, e.g. `MetadataTemplateKt`).
 */
@OptIn(ExperimentalUuidApi::class)
class UnconfiguredLlmPortTest :
    FunSpec({
        val video =
            VideoInfo(
                videoId = VideoId("video-1"),
                extractor = Extractor.YOUTUBE,
                title = "Artist - Title",
                channelId = ChannelId("channel-1"),
                channelName = "Channel",
                uploadDate = null,
                duration = 60.seconds,
                webpageUrl = Url("https://example.com/watch?v=video-1"),
            )

        test("refuses every suggestion with LlmError(provider = none)") {
            UnconfiguredLlmPort.suggestMetadata(video) shouldBe
                DomainError.LlmError(provider = "none", message = "LLM is not configured").left()
        }

        test("a preview without a matching rule is what the null port produced: FALLBACK from the empty template") {
            val preview =
                PreviewUseCase(
                    videoInfoExtractor = object : VideoInfoExtractor {
                        override suspend fun extract(url: String): Either<DomainError, VideoInfo> = video.right()
                    },
                    videoInfoCache = NoCache,
                    ruleMatchingService = RuleMatchingService(NoRules, NoChannels),
                    metadataResolver = MetadataResolver(),
                    llmPort = UnconfiguredLlmPort,
                    txRunner = InlineTransactions,
                )

            val result = preview(video.webpageUrl.value, WorkspaceId(Uuid.random()))

            val metadata = MetadataResolver().resolve(video, MetadataTemplate.Other())
            result shouldBe
                PreviewResult(
                    videoInfo = video,
                    metadata = metadata,
                    metadataSource = MetadataSource.FALLBACK,
                    matchedRule = null,
                    outputs = OutputDefaults.defaultFor(metadata.category),
                ).right()
        }
    })

private object InlineTransactions : TransactionRunner {
    override suspend fun <T> inRoTransaction(block: suspend RoTransactionScope.() -> T): T =
        block(object : RoTransactionScope {})

    override suspend fun <T> inRwTransaction(block: suspend RwTransactionScope.() -> T): T =
        block(object : RwTransactionScope {})
}

private object NoCache : VideoInfoCache {
    override suspend fun get(url: String): VideoInfo? = null

    override suspend fun put(url: String, videoInfo: VideoInfo) = Unit

    override suspend fun updateActualFormat(url: String, actualFormat: VideoInfo.Format) = Unit
}

private object NoRules : RuleRepository {
    override suspend fun findById(id: RuleId): Rule? = null

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Rule> = emptyList()

    override suspend fun findAllEnabled(): List<Rule> = emptyList()

    override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId): List<Rule> = emptyList()

    override suspend fun save(rule: Rule): Either<DomainError, Rule> = rule.right()

    override suspend fun delete(id: RuleId): Boolean = false
}

private object NoChannels : ChannelRepository {
    override suspend fun findById(id: ChannelDirectoryEntryId): Channel? = null

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Channel> = emptyList()

    override suspend fun findByChannelId(
        workspaceId: WorkspaceId,
        channelId: ChannelId,
        extractor: Extractor,
    ): Channel? = null

    override suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag): List<Channel> = emptyList()

    override suspend fun findByTags(workspaceId: WorkspaceId, tags: Set<Tag>, matchAll: Boolean): List<Channel> =
        emptyList()

    override suspend fun save(channel: Channel): Either<DomainError, Channel> = channel.right()

    override suspend fun delete(id: ChannelDirectoryEntryId): Boolean = false

    override suspend fun findAllTags(workspaceId: WorkspaceId): Set<Tag> = emptySet()
}
