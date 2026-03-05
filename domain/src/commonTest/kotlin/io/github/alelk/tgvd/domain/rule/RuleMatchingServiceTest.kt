package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.OutputDefaults
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RuleMatchingServiceTest : FunSpec({

    fun videoInfo(
        channelId: String = "UC_music",
        channelName: String = "MusicChannel",
        title: String = "Amazing Song - Great Artist",
        extractor: Extractor = Extractor.YOUTUBE,
    ) = VideoInfo(
        videoId = VideoId("vid1"),
        extractor = extractor,
        title = title,
        channelId = ChannelId(channelId),
        channelName = channelName,
        uploadDate = null,
        duration = 240.seconds,
        webpageUrl = Url("https://youtube.com/watch?v=vid1"),
    )

    fun rule(
        name: String = "rule",
        priority: Int = 0,
        match: RuleMatch,
        workspaceId: WorkspaceId = WorkspaceId(Uuid.random()),
        enabled: Boolean = true,
    ): Rule {
        val now = Clock.System.now()
        return Rule(
            id = RuleId(Uuid.random()),
            name = name,
            workspaceId = workspaceId,
            match = match,
            metadataTemplate = MetadataTemplate.Other(),
            outputs = OutputDefaults.OTHER,
            enabled = enabled,
            priority = priority,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun fakeChannelRepo(channels: List<Channel> = emptyList()): ChannelRepository {
        return object : ChannelRepository {
            override suspend fun findById(id: ChannelDirectoryEntryId) = channels.find { it.id == id }
            override suspend fun findByWorkspace(workspaceId: WorkspaceId) = channels.filter { it.workspaceId == workspaceId }
            override suspend fun findByChannelId(workspaceId: WorkspaceId, channelId: ChannelId, extractor: Extractor) =
                channels.find { it.workspaceId == workspaceId && it.channelId == channelId && it.extractor == extractor }
            override suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag) =
                channels.filter { it.workspaceId == workspaceId && tag in it.tags }
            override suspend fun findByTags(workspaceId: WorkspaceId, tags: Set<Tag>, matchAll: Boolean) =
                channels.filter { it.workspaceId == workspaceId && if (matchAll) it.tags.containsAll(tags) else it.tags.any { t -> t in tags } }
            override suspend fun save(channel: Channel) = Either.Right(channel)
            override suspend fun delete(id: ChannelDirectoryEntryId) = true
            override suspend fun findAllTags(workspaceId: WorkspaceId) = channels.filter { it.workspaceId == workspaceId }.flatMap { it.tags }.toSet()
        }
    }

    fun service(rules: List<Rule>, channels: List<Channel> = emptyList()): RuleMatchingService {
        val repo = object : RuleRepository {
            override suspend fun findById(id: RuleId) = rules.find { it.id == id }
            override suspend fun findByWorkspace(workspaceId: WorkspaceId) = rules.filter { it.workspaceId == workspaceId }
            override suspend fun findAllEnabled() = rules.filter { it.enabled }
            override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId) = rules.filter { it.enabled && it.workspaceId == workspaceId }
            override suspend fun save(rule: Rule) = Either.Right(rule)
            override suspend fun delete(id: RuleId) = true
        }
        return RuleMatchingService(repo, fakeChannelRepo(channels))
    }

    fun channel(
        workspaceId: WorkspaceId,
        channelId: String = "UC_music",
        extractor: Extractor = Extractor.YOUTUBE,
        tags: Set<Tag> = emptySet(),
        metadataOverrides: MetadataTemplate? = null,
    ): Channel {
        val now = Clock.System.now()
        return Channel(
            id = ChannelDirectoryEntryId(Uuid.random()),
            workspaceId = workspaceId,
            channelId = ChannelId(channelId),
            extractor = extractor,
            name = "Test Channel",
            tags = tags,
            metadataOverrides = metadataOverrides,
            createdAt = now,
            updatedAt = now,
        )
    }

    context("findMatchingRule") {
        test("returns rule with highest priority") {
            val wsId = WorkspaceId(Uuid.random())
            val low = rule(name = "low", priority = 1, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId)
            val high = rule(name = "high", priority = 10, match = RuleMatch.ChannelName("MusicChannel"), workspaceId = wsId)

            val result = service(listOf(low, high)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.rule.name shouldBe "high"
        }

        test("uses specificity as tiebreaker at same priority") {
            val wsId = WorkspaceId(Uuid.random())
            val titleMatch = rule(name = "titleMatch", priority = 5, match = RuleMatch.TitleRegex(".*Song.*"), workspaceId = wsId)
            val channelMatch = rule(name = "channelMatch", priority = 5, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId)

            val result = service(listOf(titleMatch, channelMatch)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.rule.name shouldBe "channelMatch"
        }

        test("returns null when no rules match") {
            val wsId = WorkspaceId(Uuid.random())
            val r = rule(match = RuleMatch.ChannelId("UC_other"), workspaceId = wsId)

            val result = service(listOf(r)).findMatchingRule(videoInfo(), wsId)

            result.shouldBeNull()
        }

        test("ignores disabled rules") {
            val wsId = WorkspaceId(Uuid.random())
            val disabled = rule(name = "disabled", priority = 100, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId, enabled = false)
            val enabled = rule(name = "enabled", priority = 1, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId, enabled = true)

            val result = service(listOf(disabled, enabled)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.rule.name shouldBe "enabled"
        }

        test("ignores rules from other workspace") {
            val wsId = WorkspaceId(Uuid.random())
            val otherWs = WorkspaceId(Uuid.random())
            val other = rule(name = "other", priority = 100, match = RuleMatch.ChannelId("UC_music"), workspaceId = otherWs)
            val ours = rule(name = "ours", priority = 1, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId)

            val result = service(listOf(other, ours)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.rule.name shouldBe "ours"
        }
    }

    context("HasTag matching via channel directory") {
        test("matches when channel has the required tag") {
            val wsId = WorkspaceId(Uuid.random())
            val musicTag = Tag("music-video")
            val ch = channel(wsId, tags = setOf(musicTag))
            val r = rule(name = "music", match = RuleMatch.HasTag(musicTag), workspaceId = wsId)

            val result = service(listOf(r), listOf(ch)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.rule.name shouldBe "music"
            result.channel.shouldNotBeNull()
            result.channel.tags shouldBe setOf(musicTag)
        }

        test("does not match when channel lacks the tag") {
            val wsId = WorkspaceId(Uuid.random())
            val ch = channel(wsId, tags = setOf(Tag("lofi")))
            val r = rule(name = "music", match = RuleMatch.HasTag(Tag("music-video")), workspaceId = wsId)

            val result = service(listOf(r), listOf(ch)).findMatchingRule(videoInfo(), wsId)

            result.shouldBeNull()
        }

        test("does not match when channel not in directory") {
            val wsId = WorkspaceId(Uuid.random())
            val r = rule(name = "music", match = RuleMatch.HasTag(Tag("music-video")), workspaceId = wsId)

            val result = service(listOf(r)).findMatchingRule(videoInfo(), wsId)

            result.shouldBeNull()
        }

        test("channel metadata overrides are returned in MatchResult") {
            val wsId = WorkspaceId(Uuid.random())
            val musicTag = Tag("music-video")
            val overrides = MetadataTemplate.MusicVideo(artistOverride = "Adele")
            val ch = channel(wsId, tags = setOf(musicTag), metadataOverrides = overrides)
            val r = rule(name = "music", match = RuleMatch.HasTag(musicTag), workspaceId = wsId)

            val result = service(listOf(r), listOf(ch)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.channel.shouldNotBeNull()
            result.channel.metadataOverrides shouldBe overrides
        }

        test("AllOf with HasTag and TitleRegex") {
            val wsId = WorkspaceId(Uuid.random())
            val musicTag = Tag("music-video")
            val ch = channel(wsId, tags = setOf(musicTag))
            val r = rule(
                name = "music-song",
                match = RuleMatch.AllOf(listOf(
                    RuleMatch.HasTag(musicTag),
                    RuleMatch.TitleRegex(".*Song.*"),
                )),
                workspaceId = wsId,
            )

            val result = service(listOf(r), listOf(ch)).findMatchingRule(videoInfo(title = "Amazing Song"), wsId)
            result.shouldNotBeNull()
            result.rule.name shouldBe "music-song"

            // Does not match when title doesn't match
            val noMatch = service(listOf(r), listOf(ch)).findMatchingRule(videoInfo(title = "Podcast Episode"), wsId)
            noMatch.shouldBeNull()
        }
    }
})
