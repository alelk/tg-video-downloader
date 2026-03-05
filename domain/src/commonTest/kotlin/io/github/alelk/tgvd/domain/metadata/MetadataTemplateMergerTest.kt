package io.github.alelk.tgvd.domain.metadata

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MetadataTemplateMergerTest : FunSpec({

    context("mergeTemplates") {

        test("returns base when overlay is null") {
            val base = MetadataTemplate.MusicVideo(artistOverride = "Base Artist")
            mergeTemplates(base, null) shouldBe base
        }

        context("MusicVideo") {
            test("overlay fields take priority") {
                val base = MetadataTemplate.MusicVideo(
                    artistOverride = "Base Artist",
                    titleOverride = "Base Title",
                    defaultTags = listOf("rock"),
                )
                val overlay = MetadataTemplate.MusicVideo(
                    artistOverride = "Overlay Artist",
                )
                val result = mergeTemplates(base, overlay) as MetadataTemplate.MusicVideo

                result.artistOverride shouldBe "Overlay Artist"
                result.titleOverride shouldBe "Base Title" // not overridden
                result.defaultTags shouldBe listOf("rock") // overlay empty → fallback to base
            }

            test("overlay patterns take priority") {
                val base = MetadataTemplate.MusicVideo(artistPattern = "base-pattern")
                val overlay = MetadataTemplate.MusicVideo(artistPattern = "overlay-pattern")
                val result = mergeTemplates(base, overlay) as MetadataTemplate.MusicVideo

                result.artistPattern shouldBe "overlay-pattern"
            }

            test("overlay defaultTags replace base when non-empty") {
                val base = MetadataTemplate.MusicVideo(defaultTags = listOf("rock"))
                val overlay = MetadataTemplate.MusicVideo(defaultTags = listOf("pop", "dance"))
                val result = mergeTemplates(base, overlay) as MetadataTemplate.MusicVideo

                result.defaultTags shouldBe listOf("pop", "dance")
            }

            test("when base is different category, overlay wins") {
                val base = MetadataTemplate.Other(titleOverride = "Other Title")
                val overlay = MetadataTemplate.MusicVideo(artistOverride = "Artist")
                val result = mergeTemplates(base, overlay) as MetadataTemplate.MusicVideo

                result.artistOverride shouldBe "Artist"
                result.titleOverride shouldBe null // base is Other, can't merge fields
            }
        }

        context("SeriesEpisode") {
            test("overlay fields take priority") {
                val base = MetadataTemplate.SeriesEpisode(
                    seriesNameOverride = "Base Series",
                    seasonPattern = "S(\\d+)",
                )
                val overlay = MetadataTemplate.SeriesEpisode(
                    seriesNameOverride = "Overlay Series",
                )
                val result = mergeTemplates(base, overlay) as MetadataTemplate.SeriesEpisode

                result.seriesNameOverride shouldBe "Overlay Series"
                result.seasonPattern shouldBe "S(\\d+)" // not overridden
            }
        }

        context("Other") {
            test("overlay fields take priority") {
                val base = MetadataTemplate.Other(titleOverride = "Base")
                val overlay = MetadataTemplate.Other(titleOverride = "Overlay")
                val result = mergeTemplates(base, overlay) as MetadataTemplate.Other

                result.titleOverride shouldBe "Overlay"
            }
        }
    }
})

