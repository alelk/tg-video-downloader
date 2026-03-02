package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.StoragePolicy
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
    ) = VideoInfo(
        videoId = VideoId("vid1"),
        extractor = Extractor.YOUTUBE,
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
            storagePolicy = StoragePolicy.OTHER_DEFAULT,
            enabled = enabled,
            priority = priority,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun service(rules: List<Rule>): RuleMatchingService {
        val repo = object : RuleRepository {
            override suspend fun findById(id: RuleId) = rules.find { it.id == id }
            override suspend fun findByWorkspace(workspaceId: WorkspaceId) = rules.filter { it.workspaceId == workspaceId }
            override suspend fun findAllEnabled() = rules.filter { it.enabled }
            override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId) = rules.filter { it.enabled && it.workspaceId == workspaceId }
            override suspend fun save(rule: Rule) = Either.Right(rule)
            override suspend fun delete(id: RuleId) = true
        }
        return RuleMatchingService(repo)
    }

    context("findMatchingRule") {
        test("returns rule with highest priority") {
            val wsId = WorkspaceId(Uuid.random())
            val low = rule(name = "low", priority = 1, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId)
            val high = rule(name = "high", priority = 10, match = RuleMatch.ChannelName("MusicChannel"), workspaceId = wsId)

            val result = service(listOf(low, high)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.name shouldBe "high"
        }

        test("uses specificity as tiebreaker at same priority") {
            val wsId = WorkspaceId(Uuid.random())
            val titleMatch = rule(name = "titleMatch", priority = 5, match = RuleMatch.TitleRegex(".*Song.*"), workspaceId = wsId)
            val channelMatch = rule(name = "channelMatch", priority = 5, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId)

            val result = service(listOf(titleMatch, channelMatch)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.name shouldBe "channelMatch"
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
            result.name shouldBe "enabled"
        }

        test("ignores rules from other workspace") {
            val wsId = WorkspaceId(Uuid.random())
            val otherWs = WorkspaceId(Uuid.random())
            val other = rule(name = "other", priority = 100, match = RuleMatch.ChannelId("UC_music"), workspaceId = otherWs)
            val ours = rule(name = "ours", priority = 1, match = RuleMatch.ChannelId("UC_music"), workspaceId = wsId)

            val result = service(listOf(other, ours)).findMatchingRule(videoInfo(), wsId)

            result.shouldNotBeNull()
            result.name shouldBe "ours"
        }
    }
})

