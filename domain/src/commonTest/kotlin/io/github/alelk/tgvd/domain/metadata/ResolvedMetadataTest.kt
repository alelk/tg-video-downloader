package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.metadata.test.resolvedCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ResolvedMetadataTest : FunSpec({

    context("MusicVideo validation") {
        test("throws on blank artist") {
            shouldThrow<IllegalArgumentException> {
                ResolvedMetadata.MusicVideo(artist = "  ", title = "Title")
            }
        }

        test("throws on blank title") {
            shouldThrow<IllegalArgumentException> {
                ResolvedMetadata.MusicVideo(artist = "Artist", title = "  ")
            }
        }
    }

    context("SeriesEpisode validation") {
        test("throws on blank seriesName") {
            shouldThrow<IllegalArgumentException> {
                ResolvedMetadata.SeriesEpisode(seriesName = " ", title = "Title")
            }
        }

        test("throws on blank title") {
            shouldThrow<IllegalArgumentException> {
                ResolvedMetadata.SeriesEpisode(seriesName = "Series", title = "  ")
            }
        }
    }

    context("Other validation") {
        test("throws on blank title") {
            shouldThrow<IllegalArgumentException> {
                ResolvedMetadata.Other(title = "  ")
            }
        }
    }

    context("category extension") {
        test("MusicVideo -> MUSIC_VIDEO") {
            val m = ResolvedMetadata.MusicVideo(artist = "A", title = "T")
            m.resolvedCategory() shouldBe Category.MUSIC_VIDEO
        }

        test("SeriesEpisode -> SERIES") {
            val m = ResolvedMetadata.SeriesEpisode(seriesName = "S", title = "T")
            m.resolvedCategory() shouldBe Category.SERIES
        }

        test("Other -> OTHER") {
            val m = ResolvedMetadata.Other(title = "T")
            m.resolvedCategory() shouldBe Category.OTHER
        }
    }

    context("year extraction") {
        test("extracts year from releaseDate") {
            val m = ResolvedMetadata.Other(title = "T", releaseDate = LocalDate("2024-06-15"))
            m.year shouldBe 2024
        }

        test("year is null without releaseDate") {
            val m = ResolvedMetadata.Other(title = "T")
            m.year shouldBe null
        }
    }
})






