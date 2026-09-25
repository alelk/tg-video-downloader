package io.github.alelk.tgvd.domain.preview

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.tx.RoTransactionScope
import io.github.alelk.tgvd.domain.tx.RwTransactionScope
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PreviewUseCaseTest : FunSpec({
    val url = "https://example.com/video"
    val workspaceId = WorkspaceId(Uuid.random())
    val video = VideoInfo(
        videoId = VideoId("video-1"),
        extractor = Extractor.YOUTUBE,
        title = "Artist - Title",
        channelId = ChannelId("channel-1"),
        channelName = "Channel",
        uploadDate = null,
        duration = 60.seconds,
        webpageUrl = Url(url),
    )

    test("extracts outside transaction and scopes cache and rule access") {
        val transactions = RecordingTransactionRunner()
        val calls = mutableListOf<String>()
        val cache = RecordingCache(transactions, calls, cached = null)
        val useCase = PreviewUseCase(
            videoInfoExtractor = object : VideoInfoExtractor {
                override suspend fun extract(url: String): Either<DomainError, VideoInfo> {
                    transactions.active shouldBe null
                    url shouldBe video.webpageUrl.value
                    calls += "extract"
                    return video.right()
                }
            },
            videoInfoCache = cache,
            ruleMatchingService = RuleMatchingService(
                ruleRepository = EmptyRuleRepository(transactions, calls),
                channelRepository = EmptyChannelRepository(transactions, calls),
            ),
            metadataResolver = MetadataResolver(),
            llmPort = null,
            txRunner = transactions,
        )

        useCase(url, workspaceId).isRight() shouldBe true

        calls.shouldContainExactly("cache.get", "extract", "cache.put", "rules.find", "channels.find")
        cache.stored shouldBe video
    }

    test("uses cached video without invoking extractor or write transaction") {
        val transactions = RecordingTransactionRunner()
        val calls = mutableListOf<String>()
        val cache = RecordingCache(transactions, calls, cached = video)
        val useCase = PreviewUseCase(
            videoInfoExtractor = object : VideoInfoExtractor {
                override suspend fun extract(url: String): Either<DomainError, VideoInfo> =
                    error("extractor must not be called on a cache hit")
            },
            videoInfoCache = cache,
            ruleMatchingService = RuleMatchingService(
                ruleRepository = EmptyRuleRepository(transactions, calls),
                channelRepository = EmptyChannelRepository(transactions, calls),
            ),
            metadataResolver = MetadataResolver(),
            llmPort = null,
            txRunner = transactions,
        )

        useCase(url, workspaceId).isRight() shouldBe true

        calls.shouldContainExactly("cache.get", "rules.find", "channels.find")
        transactions.modes.shouldContainExactly(Mode.READ_ONLY, Mode.READ_ONLY)
    }
})

private enum class Mode { READ_ONLY, READ_WRITE }

private class RecordingTransactionRunner : TransactionRunner {
    var active: Mode? = null
        private set
    val modes = mutableListOf<Mode>()

    override suspend fun <T> inRoTransaction(block: suspend RoTransactionScope.() -> T): T =
        runIn(Mode.READ_ONLY) { block(object : RoTransactionScope {}) }

    override suspend fun <T> inRwTransaction(block: suspend RwTransactionScope.() -> T): T =
        runIn(Mode.READ_WRITE) { block(object : RwTransactionScope {}) }

    private suspend fun <T> runIn(mode: Mode, block: suspend () -> T): T {
        active shouldBe null
        active = mode
        modes += mode
        return try {
            block()
        } finally {
            active = null
        }
    }
}

private class RecordingCache(
    private val transactions: RecordingTransactionRunner,
    private val calls: MutableList<String>,
    private val cached: VideoInfo?,
) : VideoInfoCache {
    var stored: VideoInfo? = null
        private set

    override suspend fun get(url: String): VideoInfo? {
        transactions.active shouldBe Mode.READ_ONLY
        calls += "cache.get"
        return cached
    }

    override suspend fun put(url: String, videoInfo: VideoInfo) {
        transactions.active shouldBe Mode.READ_WRITE
        calls += "cache.put"
        stored = videoInfo
    }

    override suspend fun updateActualFormat(url: String, actualFormat: VideoInfo.Format) = Unit
}

private class EmptyRuleRepository(
    private val transactions: RecordingTransactionRunner,
    private val calls: MutableList<String>,
) : RuleRepository {
    override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId): List<Rule> {
        transactions.active shouldBe Mode.READ_ONLY
        calls += "rules.find"
        return emptyList()
    }

    override suspend fun findById(id: RuleId): Rule? = null
    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Rule> = emptyList()
    override suspend fun findAllEnabled(): List<Rule> = emptyList()
    override suspend fun save(rule: Rule): Either<DomainError, Rule> = rule.right()
    override suspend fun delete(id: RuleId): Boolean = false
}

private class EmptyChannelRepository(
    private val transactions: RecordingTransactionRunner,
    private val calls: MutableList<String>,
) : ChannelRepository {
    override suspend fun findByChannelId(
        workspaceId: WorkspaceId,
        channelId: ChannelId,
        extractor: Extractor,
    ): Channel? {
        transactions.active shouldBe Mode.READ_ONLY
        calls += "channels.find"
        return null
    }

    override suspend fun findById(id: ChannelDirectoryEntryId): Channel? = null
    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Channel> = emptyList()
    override suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag): List<Channel> = emptyList()
    override suspend fun findByTags(workspaceId: WorkspaceId, tags: Set<Tag>, matchAll: Boolean): List<Channel> = emptyList()
    override suspend fun save(channel: Channel): Either<DomainError, Channel> = channel.right()
    override suspend fun delete(id: ChannelDirectoryEntryId): Boolean = false
    override suspend fun findAllTags(workspaceId: WorkspaceId): Set<Tag> = emptySet()
}
