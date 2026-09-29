package io.github.alelk.tgvd.api.mapping.preview

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.fixtures.FIXED_INSTANT
import io.github.alelk.tgvd.domain.fixtures.aJob
import io.github.alelk.tgvd.domain.fixtures.aStoragePlan
import io.github.alelk.tgvd.domain.fixtures.aVideoInfo
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.preview.PreviewResult
import io.github.alelk.tgvd.domain.preview.UserOverrides
import io.github.alelk.tgvd.domain.preview.VideoPreview
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputDefaults
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class VideoPreviewMappingTest :
    FunSpec({
        val workspace = aWorkspace()
        val video = aVideoInfo()
        val rule =
            Rule(
                id = RuleId(Uuid.random()),
                name = "Music",
                workspaceId = workspace.id,
                match = RuleMatch.ChannelId("channel-1"),
                metadataTemplate = MetadataTemplate.MusicVideo(),
                outputs = OutputDefaults.MUSIC_VIDEO,
                createdAt = FIXED_INSTANT,
                updatedAt = FIXED_INSTANT,
            )
        val metadata = ResolvedMetadata.MusicVideo(artist = "Artist", title = "Title")
        val finished = aJob(workspace, status = JobStatus.CANCELLED)
        val plan = aStoragePlan()
        val preview =
            VideoPreview(
                source = VideoSource(Url("https://example.com/v"), video.videoId, video.extractor),
                result = PreviewResult(video, metadata, MetadataSource.RULE, rule, rule.outputs),
                storagePlan = plan.copy(original = plan.original.copy(maxQuality = DownloadPolicy.VideoQuality.HD_720)),
                appliedOverrides = UserOverrides.MusicVideo(artist = "Artist"),
                previousDownloads = listOf(finished),
                defaultMediaSelection = MediaSelection(audioFormatIds = null, subtitleLanguages = listOf("en")),
            )

        test("maps every part of the preview response") {
            val dto = preview.toDto()

            dto.source.url shouldBe "https://example.com/v"
            dto.videoInfo.videoId shouldBe "video-1"
            dto.matchedRule shouldBe RuleSummaryDto(id = rule.id.value.toString(), name = "Music")
            dto.metadataSource shouldBe MetadataSourceDto.RULE
            dto.category shouldBe CategoryDto.MUSIC_VIDEO
            dto.storagePlan.original.maxQuality shouldBe VideoQualityDto.HD_720
            dto.appliedOverrides shouldBe UserOverridesDto.MusicVideo(artist = "Artist")
            dto.warnings shouldBe emptyList()
            dto.defaultMediaSelection shouldBe
                MediaSelectionDto(audioFormatIds = null, subtitleLanguages = listOf("en"))
        }

        test("a previous download carries the upper-case status name and the original output's format") {
            preview.toDto().previousDownloads shouldBe
                listOf(
                    DownloadHistoryEntryDto(
                        jobId = finished.id.value.toString(),
                        status = "CANCELLED",
                        finishedAt = FIXED_INSTANT.toString(),
                        maxQuality = null,
                        formatSummary = "mkv",
                    ),
                )
        }
    })
