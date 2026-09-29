package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

/** `POST …/preview` through the real module over PostgreSQL, yt-dlp faked. */
class PreviewRoutesTest :
    FunSpec({
        val app = routeTestApp()

        suspend fun preview(slug: String, request: PreviewRequestDto) = app.client.post("$WORKSPACES/$slug/preview") {
            asDevUser()
            jsonBody(request)
        }

        test("without a rule: fallback metadata, default outputs, cached extraction, force re-extracts") {
            app.client.createWorkspace("pv-fallback")
            app.knowsVideo("pv-1")

            val response = preview("pv-fallback", PreviewRequestDto(url = videoUrl("pv-1")))
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<PreviewResponseDto>()
            body.source.videoId shouldBe "pv-1"
            body.source.extractor shouldBe "youtube"
            body.videoInfo.title shouldBe "Video pv-1"
            body.videoInfo.availableFormats.size shouldBe 3
            body.matchedRule.shouldBeNull()
            body.metadataSource shouldBe MetadataSourceDto.FALLBACK
            body.category shouldBe CategoryDto.OTHER
            body.metadata shouldBe ResolvedMetadataDto.Other(title = "Video pv-1", releaseDate = "2024-05-06")
            body.storagePlan.original.path shouldBe "~/Downloads/Media/Videos/Route Tests/Video pv-1 [pv-1].mkv"
            body.storagePlan.additional shouldBe emptyList()
            body.appliedOverrides.shouldBeNull()
            body.previousDownloads shouldBe emptyList()
            body.defaultMediaSelection shouldBe
                MediaSelectionDto(audioFormatIds = listOf("140-0"), subtitleLanguages = listOf("en", "ru"))

            preview("pv-fallback", PreviewRequestDto(url = videoUrl("pv-1"))).status shouldBe HttpStatusCode.OK
            app.extractor.calls[videoUrl("pv-1")] shouldBe 1

            preview("pv-fallback", PreviewRequestDto(url = videoUrl("pv-1"), force = true)).status shouldBe
                HttpStatusCode.OK
            app.extractor.calls[videoUrl("pv-1")] shouldBe 2
        }

        test("a matching rule drives metadata and outputs; user overrides win") {
            app.client.createWorkspace("pv-rule")
            val rule = app.client.createRule("pv-rule", aCreateRuleRequest(channelId = "UC-pv-rule"))
            app.knowsVideo("pv-2", channelId = "UC-pv-rule")

            val response =
                preview(
                    "pv-rule",
                    PreviewRequestDto(
                        url = videoUrl("pv-2"),
                        overrides = UserOverridesDto.MusicVideo(title = "Overridden"),
                    ),
                )
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<PreviewResponseDto>()
            body.matchedRule shouldBe RuleSummaryDto(id = rule.id, name = rule.name)
            body.metadataSource shouldBe MetadataSourceDto.RULE
            body.category shouldBe CategoryDto.MUSIC_VIDEO
            body.metadata shouldBe
                ResolvedMetadataDto.MusicVideo(
                    artist = "Route Artist",
                    title = "Overridden",
                    releaseDate = "2024-05-06",
                    tags = listOf("music"),
                )
            body.appliedOverrides shouldBe UserOverridesDto.MusicVideo(title = "Overridden")
            body.storagePlan.original.path shouldBe "/media/music/Route Artist/Overridden [pv-2].mkv"
            body.defaultMediaSelection shouldBe
                MediaSelectionDto(audioFormatIds = listOf("140-0", "140-1"), subtitleLanguages = listOf("en"))
        }

        test("finished jobs of the same video show up as previous downloads") {
            app.client.createWorkspace("pv-history")
            app.knowsVideo("pv-3")
            val job = app.client.createJob("pv-history", "pv-3")
            app.client.post("$WORKSPACES/pv-history/jobs/${job.id}/cancel") { asDevUser() }.status shouldBe
                HttpStatusCode.OK

            val body = preview("pv-history", PreviewRequestDto(url = videoUrl("pv-3"))).body<PreviewResponseDto>()
            body.previousDownloads.map { it.jobId to it.status } shouldBe listOf(job.id to "CANCELLED")
            body.previousDownloads.single().formatSummary shouldBe "mkv"
        }

        test("a URL yt-dlp cannot extract is 422 VIDEO_UNAVAILABLE") {
            app.client.createWorkspace("pv-unknown")
            val response = preview("pv-unknown", PreviewRequestDto(url = "https://www.youtube.com/watch?v=nope"))
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.body<ApiErrorDto>().error.code shouldBe "VIDEO_UNAVAILABLE"
        }
    })
