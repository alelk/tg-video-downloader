package io.github.alelk.tgvd.api.contract.metadata

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MetadataTemplateDtoTest : FunSpec({

    context("MusicVideo") {
        test("serializes with type discriminator") {
            val dto: MetadataTemplateDto = MetadataTemplateDto.MusicVideo(
                artistOverride = "Various Artists",
                artistPattern = "(.+?) - .+",
                titlePattern = ".+ - (.+)",
                defaultTags = listOf("music"),
            )
            val json = apiJson.encodeToString(MetadataTemplateDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "music-video",
                    "artistOverride": "Various Artists",
                    "artistPattern": "(.+?) - .+",
                    "titleOverride": null,
                    "titlePattern": ".+ - (.+)",
                    "defaultTags": ["music"]
                }
            """
        }

        test("deserializes with defaults") {
            val json = """
                {
                    "type": "music-video"
                }
            """
            val dto = apiJson.decodeFromString(MetadataTemplateDto.serializer(), json)
            dto shouldBe MetadataTemplateDto.MusicVideo()
        }
    }

    context("SeriesEpisode") {
        test("round-trip") {
            val original: MetadataTemplateDto = MetadataTemplateDto.SeriesEpisode(
                seriesNameOverride = "My Series",
                seasonPattern = "S(\\d+)",
                episodePattern = "E(\\d+)",
            )
            val json = apiJson.encodeToString(MetadataTemplateDto.serializer(), original)
            val decoded = apiJson.decodeFromString(MetadataTemplateDto.serializer(), json)
            decoded shouldBe original
        }
    }

    context("Other") {
        test("round-trip") {
            val original: MetadataTemplateDto = MetadataTemplateDto.Other(
                titleOverride = "Fixed Title",
                defaultTags = listOf("tag1", "tag2"),
            )
            val json = apiJson.encodeToString(MetadataTemplateDto.serializer(), original)
            val decoded = apiJson.decodeFromString(MetadataTemplateDto.serializer(), json)
            decoded shouldBe original
        }
    }
})

