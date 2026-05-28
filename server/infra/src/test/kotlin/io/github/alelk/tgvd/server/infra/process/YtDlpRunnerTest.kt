package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toVideoInfoPm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class YtDlpRunnerTest : FunSpec({

    val runner = YtDlpRunner(mockk())

    test("resolveBestFormatId selects best video and audio for BEST quality") {
        val formats = listOf(
            VideoInfo.Format("1", "mp4", height = 360, vcodec = "avc1", acodec = "mp4a"), // muxed 360p
            VideoInfo.Format("2", "mp4", height = 2160, vcodec = "vp9", acodec = "none"), // 4k video
            VideoInfo.Format("3", "mp4", height = 1080, vcodec = "avc1", acodec = "none"), // 1080p video
            VideoInfo.Format("4", "m4a", vcodec = "none", acodec = "mp4a", tbr = 128.0), // 128k audio
            VideoInfo.Format("5", "m4a", vcodec = "none", acodec = "mp4a", tbr = 256.0), // 256k audio
        )

        val result = runner.resolveBestFormatId(formats, DownloadPolicy.VideoQuality.BEST)
        result shouldBe "2+5"
    }

    test("resolveBestFormatId respects resolution cap") {
        val formats = listOf(
            VideoInfo.Format("1", "mp4", height = 2160, vcodec = "vp9", acodec = "none"),
            VideoInfo.Format("2", "mp4", height = 1080, vcodec = "vp9", acodec = "none"),
            VideoInfo.Format("3", "mp4", height = 720, vcodec = "vp9", acodec = "none"),
            VideoInfo.Format("4", "m4a", vcodec = "none", acodec = "mp4a", tbr = 128.0),
        )

        val result1080 = runner.resolveBestFormatId(formats, DownloadPolicy.VideoQuality.HD_1080)
        result1080 shouldBe "2+4"

        val result720 = runner.resolveBestFormatId(formats, DownloadPolicy.VideoQuality.HD_720)
        result720 shouldBe "3+4"
    }

    test("resolveBestFormatId falls back to muxed if no separate audio") {
        val formats = listOf(
            VideoInfo.Format("1", "mp4", height = 1080, vcodec = "avc1", acodec = "mp4a"),
            VideoInfo.Format("2", "mp4", height = 720, vcodec = "avc1", acodec = "mp4a"),
        )

        val result = runner.resolveBestFormatId(formats, DownloadPolicy.VideoQuality.BEST)
        result shouldBe "1"
    }

    test("toVideoInfoPm handles blank channel info") {
        val source = io.github.alelk.tgvd.domain.video.VideoSource(
            io.github.alelk.tgvd.domain.common.Url("https://example.com"),
            io.github.alelk.tgvd.domain.common.VideoId("v1"),
            io.github.alelk.tgvd.domain.common.Extractor("test")
        )
        val metadata = ResolvedMetadata.Other(title = "test")
        val pm = source.toVideoInfoPm(metadata)
        pm.channelId shouldBe "unknown"
        pm.channelName shouldBe "unknown"

        val domain = pm.toDomain()
        domain.channelId.value shouldBe "unknown"
    }

    test("toDomain handles blank channelId from database") {
        val pm = io.github.alelk.tgvd.server.infra.db.model.VideoInfoPm(
            videoId = "v1",
            extractor = "test",
            title = "test",
            channelId = "",
            channelName = "",
            durationSeconds = 0,
            webpageUrl = "https://example.com"
        )
        val domain = pm.toDomain()
        domain.channelId.value shouldBe "unknown"
        domain.channelName shouldBe "Unknown"
    }
})
