package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.*
import io.github.alelk.tgvd.domain.video.VideoSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SaveAsRuleBuilderTest : FunSpec({

    val now = Clock.System.now()

    fun buildTestJob(meta: ResolvedMetadata): Job = Job(
        id = JobId(kotlin.uuid.Uuid.random()),
        workspaceId = WorkspaceId(kotlin.uuid.Uuid.random()),
        createdBy = TelegramUserId(12345L),
        source = VideoSource(
            url = Url("https://youtube.com/watch?v=dQw4w9WgXcQ"),
            videoId = VideoId("dQw4w9WgXcQ"),
            extractor = Extractor("youtube"),
        ),
        metadata = meta,
        metadataSource = MetadataSource.RULE,
        storagePlan = StoragePlan(
            original = OutputTarget(
                path = FilePath("/media/music/Rick Astley/Never Gonna Give You Up [dQw4w9WgXcQ].webm"),
                format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
            ),
            additional = listOf(
                OutputTarget(
                    path = FilePath("/media/music/converted/Rick Astley/Never Gonna Give You Up.mp4"),
                    format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
                    embedMetadata = true,
                ),
            ),
        ),
        createdAt = now,
        updatedAt = now,
    )

    context("buildSaveAsRuleRequest") {
        test("creates rule with channel id match") {
            val job = buildTestJob(ResolvedMetadata.MusicVideo(artist = "Rick Astley", title = "Never Gonna Give You Up"))
            val match = RuleMatch.ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw")

            val request = buildSaveAsRuleRequest(
                workspaceId = job.workspaceId,
                match = match,
                job = job,
            )

            request.workspaceId shouldBe job.workspaceId
            request.match shouldBe match
            request.outputs.size shouldBe 2
            request.name shouldContain "youtube"
        }

        test("replaces videoId with placeholder in path templates") {
            val job = buildTestJob(ResolvedMetadata.Other(title = "Test Video"))
            val match = RuleMatch.ChannelId("someChannel")

            val request = buildSaveAsRuleRequest(
                workspaceId = job.workspaceId,
                match = match,
                job = job,
            )

            val originalTemplate = request.outputs.first().pathTemplate
            originalTemplate shouldContain "{videoId}"
            originalTemplate.contains("dQw4w9WgXcQ") shouldBe false
        }

        test("metadata template contains artist override for MusicVideo") {
            val job = buildTestJob(ResolvedMetadata.MusicVideo(artist = "Rick Astley", title = "NGGU"))
            val match = RuleMatch.ChannelId("ch1")

            val request = buildSaveAsRuleRequest(
                workspaceId = job.workspaceId,
                match = match,
                job = job,
                includeMetadataTemplate = true,
            )

            val tmpl = request.metadataTemplate as? io.github.alelk.tgvd.domain.metadata.MetadataTemplate.MusicVideo
            tmpl shouldNotBe null
            tmpl!!.artistOverride shouldBe "Rick Astley"
        }

        test("without includeMetadataTemplate, template has no overrides") {
            val job = buildTestJob(ResolvedMetadata.MusicVideo(artist = "Rick Astley", title = "NGGU"))
            val match = RuleMatch.ChannelId("ch1")

            val request = buildSaveAsRuleRequest(
                workspaceId = job.workspaceId,
                match = match,
                job = job,
                includeMetadataTemplate = false,
            )

            val tmpl = request.metadataTemplate as? io.github.alelk.tgvd.domain.metadata.MetadataTemplate.MusicVideo
            tmpl shouldNotBe null
            tmpl!!.artistOverride shouldBe null
        }

        test("enabled flag is preserved") {
            val job = buildTestJob(ResolvedMetadata.Other(title = "T"))
            val request = buildSaveAsRuleRequest(
                workspaceId = job.workspaceId,
                match = RuleMatch.ChannelId("ch"),
                job = job,
                enabled = false,
            )
            request.enabled shouldBe false
        }
    }
})

