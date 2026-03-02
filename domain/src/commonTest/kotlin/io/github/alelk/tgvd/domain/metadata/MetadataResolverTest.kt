package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class MetadataResolverTest : FunSpec({

    val resolver = MetadataResolver()

    fun videoInfo(
        title: String = "Test Video",
        channelName: String = "TestChannel",
        uploadDate: LocalDate? = null,
    ) = VideoInfo(
        videoId = VideoId("v1"),
        extractor = Extractor.YOUTUBE,
        title = title,
        channelId = ChannelId("UC1"),
        channelName = channelName,
        uploadDate = uploadDate,
        duration = 200.seconds,
        webpageUrl = Url("https://youtube.com/watch?v=v1"),
    )

    context("MusicVideo resolution") {
        test("parses artist-title from 'Artist - Title' format") {
            val video = videoInfo(title = "Rick Astley - Never Gonna Give You Up")
            val template = MetadataTemplate.MusicVideo()

            val result = resolver.resolve(video, template)

            result shouldBe ResolvedMetadata.MusicVideo(
                artist = "Rick Astley",
                title = "Never Gonna Give You Up",
                releaseDate = null,
                tags = emptyList(),
            )
        }

        test("supports en-dash separator") {
            val video = videoInfo(title = "Artist \u2013 Title")
            val result = resolver.resolve(video, MetadataTemplate.MusicVideo())

            (result as ResolvedMetadata.MusicVideo).artist shouldBe "Artist"
            result.title shouldBe "Title"
        }

        test("supports em-dash separator") {
            val video = videoInfo(title = "Artist \u2014 Title")
            val result = resolver.resolve(video, MetadataTemplate.MusicVideo())

            (result as ResolvedMetadata.MusicVideo).artist shouldBe "Artist"
            result.title shouldBe "Title"
        }

        test("supports colon separator") {
            val video = videoInfo(title = "Artist: Title")
            val result = resolver.resolve(video, MetadataTemplate.MusicVideo())

            (result as ResolvedMetadata.MusicVideo).artist shouldBe "Artist"
            result.title shouldBe "Title"
        }

        test("falls back to 'Unknown Artist' when no separator found") {
            val video = videoInfo(title = "Just A Title")
            val result = resolver.resolve(video, MetadataTemplate.MusicVideo())

            (result as ResolvedMetadata.MusicVideo).artist shouldBe "Unknown Artist"
            result.title shouldBe "Just A Title"
        }

        test("artistOverride takes priority over parsing") {
            val video = videoInfo(title = "Someone - Song")
            val template = MetadataTemplate.MusicVideo(artistOverride = "Forced Artist")

            val result = resolver.resolve(video, template) as ResolvedMetadata.MusicVideo

            result.artist shouldBe "Forced Artist"
        }

        test("titleOverride takes priority over parsing") {
            val video = videoInfo(title = "Artist - Original Title")
            val template = MetadataTemplate.MusicVideo(titleOverride = "Custom Title")

            val result = resolver.resolve(video, template) as ResolvedMetadata.MusicVideo

            result.title shouldBe "Custom Title"
        }

        test("artistPattern extracts via regex group") {
            val video = videoInfo(title = "MV: Linkin Park - Numb (Official)")
            val template = MetadataTemplate.MusicVideo(artistPattern = "MV: (.+?) -")

            val result = resolver.resolve(video, template) as ResolvedMetadata.MusicVideo

            result.artist shouldBe "Linkin Park"
        }

        test("preserves upload date as releaseDate") {
            val date = LocalDate("2024-03-15")
            val video = videoInfo(title = "A - B", uploadDate = date)
            val result = resolver.resolve(video, MetadataTemplate.MusicVideo()) as ResolvedMetadata.MusicVideo

            result.releaseDate shouldBe date
        }

        test("carries defaultTags from template") {
            val template = MetadataTemplate.MusicVideo(defaultTags = listOf("rock", "live"))
            val video = videoInfo(title = "A - B")
            val result = resolver.resolve(video, template) as ResolvedMetadata.MusicVideo

            result.tags shouldBe listOf("rock", "live")
        }
    }

    context("SeriesEpisode resolution") {
        test("uses channelName as fallback seriesName") {
            val video = videoInfo(channelName = "My Show", title = "Episode 5")
            val template = MetadataTemplate.SeriesEpisode()

            val result = resolver.resolve(video, template) as ResolvedMetadata.SeriesEpisode

            result.seriesName shouldBe "My Show"
            result.title shouldBe "Episode 5"
        }

        test("seriesNameOverride takes priority") {
            val video = videoInfo(channelName = "Channel", title = "E05")
            val template = MetadataTemplate.SeriesEpisode(seriesNameOverride = "Custom Series")

            val result = resolver.resolve(video, template) as ResolvedMetadata.SeriesEpisode

            result.seriesName shouldBe "Custom Series"
        }

        test("extracts season and episode from patterns") {
            val video = videoInfo(title = "My Show S02E13 - The Finale")
            val template = MetadataTemplate.SeriesEpisode(
                seasonPattern = "S(\\d{2})",
                episodePattern = "E(\\d{2})",
            )

            val result = resolver.resolve(video, template) as ResolvedMetadata.SeriesEpisode

            result.season shouldBe "02"
            result.episode shouldBe "13"
        }

        test("season and episode are null without patterns") {
            val video = videoInfo(title = "S02E13 Episode")
            val template = MetadataTemplate.SeriesEpisode()

            val result = resolver.resolve(video, template) as ResolvedMetadata.SeriesEpisode

            result.season shouldBe null
            result.episode shouldBe null
        }
    }

    context("Other resolution") {
        test("uses video title as metadata title") {
            val video = videoInfo(title = "Random Video")
            val result = resolver.resolve(video, MetadataTemplate.Other()) as ResolvedMetadata.Other

            result.title shouldBe "Random Video"
        }

        test("titleOverride takes priority") {
            val video = videoInfo(title = "Original")
            val template = MetadataTemplate.Other(titleOverride = "Overridden")

            val result = resolver.resolve(video, template) as ResolvedMetadata.Other

            result.title shouldBe "Overridden"
        }
    }

    context("extractByPattern resilience") {
        test("invalid regex in pattern does not throw, returns fallback") {
            val video = videoInfo(title = "A - B")
            val template = MetadataTemplate.MusicVideo(artistPattern = "[invalid")

            val result = resolver.resolve(video, template) as ResolvedMetadata.MusicVideo

            // Invalid pattern silently fails → falls back to title parsing
            result.artist shouldBe "A"
        }
    }
})

