package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toVideoInfoPm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.every

class YtDlpRunnerTest : FunSpec({

    val runner = YtDlpRunner(mockk())

    test("resolveBestFormatId selects best video and audio for BEST quality") {
        val formats = listOf(
            VideoInfo.Format("1", "mp4", height = 360, vcodec = "avc1", acodec = "mp4a"), // muxed 360p
            VideoInfo.Format("2", "mp4", height = 2160, vcodec = "vp9", acodec = "none"), // 4k video
            VideoInfo.Format("3", "mp4", height = 1080, vcodec = "avc1", acodec = "none"), // 1080p video
            VideoInfo.Format("4", "m4a", vcodec = "none", acodec = "mp4a", tbr = 128.0), // 128k audio
            VideoInfo.Format("5", "m4a", vcodec = "none", acodec = "mp4a", tbr = 256.0, isOriginalAudio = true), // 256k original audio
        )

        val result = runner.resolveBestFormatId(formats, DownloadPolicy.VideoQuality.BEST)
        result shouldBe "2+5"
    }

    test("explicit audio selection includes exactly the checked formats") {
        val settings = mockk<SystemSettingsHolder>()
        every { settings.ytDlpConfig } returns YtDlpConfig()
        val selectedRunner = YtDlpRunner(settings)
        val formats = listOf(
            VideoInfo.Format("video", "webm", height = 1080, vcodec = "vp9", acodec = "none"),
            VideoInfo.Format("ru", "webm", language = "ru", vcodec = "none", acodec = "opus"),
            VideoInfo.Format("en", "m4a", language = "en", vcodec = "none", acodec = "mp4a"),
        )
        selectedRunner.selectFormats(formats, DownloadPolicy(),
            MediaSelection(audioFormatIds = listOf("en"))).formatSelector shouldBe "video+en"
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

    test("progress ignores subtitles and counts each selected media stream once") {
        val progress = MediaProgressTracker(3)
        progress.onLine("[download] Destination: video.en.vtt") shouldBe null
        progress.onLine("[download] 100.0% of 100KiB") shouldBe null
        progress.onLine("[download] Destination: video.webp") shouldBe null
        progress.onLine("[download] 100.0% of 100KiB") shouldBe null
        progress.onLine("[download] Destination: video.f270.webm") shouldBe null
        progress.onLine("[download] 50.0% of 100MiB")?.percent shouldBe 15
        progress.onLine("[download] 100.0% of 100MiB")?.percent shouldBe 31
        progress.onLine("[download] Destination: video.f251.webm") shouldBe null
        progress.onLine("[download] 100.0% of 10MiB")?.percent shouldBe 63
        progress.onLine("[download] Destination: video.f140.m4a") shouldBe null
        progress.onLine("[download] 100.0% of 10MiB")?.percent shouldBe 95
        progress.onLine("[Merger] Merging formats into video.mkv") shouldBe null
    }

    test("progress does not parse unrelated percentages") {
        val progress = MediaProgressTracker(2)
        progress.onLine("[info] 100% of subtitles fetched") shouldBe null
        progress.onLine("[download] Destination: video.f270.webm") shouldBe null
        progress.onLine("[download] 50.0% of 100MiB")?.percent shouldBe 23
        progress.onLine("[download] 40.0% of 100MiB")?.percent shouldBe 23
    }

    test("effectiveContainer always follows the output path's extension") {
        runner.effectiveContainer(FilePath("/tmp/video.mp4")) shouldBe "mp4"
        runner.effectiveContainer(FilePath("/tmp/video.mkv")) shouldBe "mkv"
        runner.effectiveContainer(FilePath("/tmp/video")) shouldBe null
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
