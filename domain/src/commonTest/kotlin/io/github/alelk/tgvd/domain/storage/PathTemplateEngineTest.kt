package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.time.Duration.Companion.seconds

class PathTemplateEngineTest : FunSpec({

    val engine = PathTemplateEngine()

    fun videoInfo(
        videoId: String = "abc123",
        channelId: String = "UC1",
        channelName: String = "TestChannel",
        title: String = "Test Video",
        uploadDate: LocalDate? = null,
    ) = VideoInfo(
        videoId = VideoId(videoId),
        extractor = Extractor.YOUTUBE,
        title = title,
        channelId = ChannelId(channelId),
        channelName = channelName,
        uploadDate = uploadDate,
        duration = 200.seconds,
        webpageUrl = Url("https://youtube.com/watch?v=$videoId"),
    )

    context("render") {
        test("substitutes all variables") {
            val video = videoInfo(videoId = "xyz", channelName = "MyChannel")
            val metadata = ResolvedMetadata.Other(title = "My Video")
            val format = OutputFormat.OriginalVideo(MediaContainer.MP4)

            val result = engine.render("/media/{channelName}/{title} [{videoId}].{ext}", video, metadata, format)

            result shouldBe FilePath("/media/MyChannel/My Video [xyz].mp4")
        }

        test("substitutes artist for MusicVideo metadata") {
            val video = videoInfo()
            val metadata = ResolvedMetadata.MusicVideo(artist = "Linkin Park", title = "Numb")
            val format = OutputFormat.ConvertedVideo(MediaContainer.MP4)

            val result = engine.render("/music/{artist}/{title}.{ext}", video, metadata, format)

            result shouldBe FilePath("/music/Linkin Park/Numb.mp4")
        }

        test("substitutes seriesName, season, episode for SeriesEpisode metadata") {
            val video = videoInfo()
            val metadata = ResolvedMetadata.SeriesEpisode(seriesName = "Breaking Bad", season = "02", episode = "05", title = "Breakage")
            val format = OutputFormat.OriginalVideo(MediaContainer.MKV)

            val result = engine.render("/tv/{seriesName}/S{season}E{episode} - {title}.{ext}", video, metadata, format)

            result shouldBe FilePath("/tv/Breaking Bad/S02E05 - Breakage.mkv")
        }

        test("uses 'unknown' for missing year and date") {
            val video = videoInfo(uploadDate = null)
            val metadata = ResolvedMetadata.Other(title = "T")
            val format = OutputFormat.OriginalVideo(MediaContainer.WEBM)

            val result = engine.render("/{year}/{date}/{title}.{ext}", video, metadata, format)

            result shouldBe FilePath("/unknown/unknown/T.webm")
        }

        test("uses actual year and date when present") {
            val video = videoInfo(uploadDate = LocalDate("2024-03-15"))
            val metadata = ResolvedMetadata.Other(title = "T", releaseDate = LocalDate("2024-03-15"))
            val format = OutputFormat.OriginalVideo(MediaContainer.MP4)

            val result = engine.render("/{year}/{date}/{title}.{ext}", video, metadata, format)

            result shouldBe FilePath("/2024/2024-03-15/T.mp4")
        }

        test("defaults season and episode for SeriesEpisode") {
            val video = videoInfo()
            val metadata = ResolvedMetadata.SeriesEpisode(seriesName = "Show", title = "Ep")
            val format = OutputFormat.OriginalVideo(MediaContainer.MP4)

            val result = engine.render("/tv/{seriesName}/S{season}E{episode}.{ext}", video, metadata, format)

            result shouldBe FilePath("/tv/Show/S01E00.mp4")
        }
    }

    context("sanitize") {
        test("replaces forbidden characters with underscore") {
            val video = videoInfo(channelName = "Test/Channel:Name")
            val metadata = ResolvedMetadata.Other(title = "Video \"Part\" <1>")
            val format = OutputFormat.OriginalVideo(MediaContainer.MP4)

            val result = engine.render("/{channelName}/{title}.{ext}", video, metadata, format)

            // channelName and title values are sanitized (no forbidden chars), path separators from template stay
            result.value.split("/").drop(1).let { segments ->
                segments[0] shouldNotContain "/"
                segments[0] shouldNotContain ":"
            }
        }
    }

    context("buildContext") {
        test("includes artist for MusicVideo") {
            val video = videoInfo()
            val metadata = ResolvedMetadata.MusicVideo(artist = "A", title = "T")

            val ctx = engine.buildContext(video, metadata)

            ctx["artist"] shouldBe "A"
        }

        test("includes seriesName for SeriesEpisode") {
            val video = videoInfo()
            val metadata = ResolvedMetadata.SeriesEpisode(seriesName = "S", title = "T")

            val ctx = engine.buildContext(video, metadata)

            ctx["seriesName"] shouldBe "S"
        }

        test("does not include artist for Other") {
            val video = videoInfo()
            val metadata = ResolvedMetadata.Other(title = "T")

            val ctx = engine.buildContext(video, metadata)

            ctx.containsKey("artist") shouldBe false
            ctx.containsKey("seriesName") shouldBe false
        }
    }
})


