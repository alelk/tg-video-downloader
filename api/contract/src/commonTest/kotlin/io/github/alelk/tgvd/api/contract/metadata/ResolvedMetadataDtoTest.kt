package io.github.alelk.tgvd.api.contract.metadata

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ResolvedMetadataDtoTest : FunSpec({

    context("MusicVideo") {
        test("serializes with type discriminator") {
            val dto = ResolvedMetadataDto.MusicVideo(
                artist = "Rick Astley",
                title = "Never Gonna Give You Up",
                releaseDate = "1987-07-27",
                tags = listOf("pop", "80s"),
                comment = "Classic",
            )
            val json = apiJson.encodeToString(ResolvedMetadataDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "music-video",
                    "artist": "Rick Astley",
                    "title": "Never Gonna Give You Up",
                    "releaseDate": "1987-07-27",
                    "tags": ["pop", "80s"],
                    "comment": "Classic"
                }
            """
        }

        test("deserializes from json") {
            val json = """
                {
                    "type": "music-video",
                    "artist": "Rick Astley",
                    "title": "Never Gonna Give You Up",
                    "tags": []
                }
            """
            val dto = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            dto shouldBe ResolvedMetadataDto.MusicVideo(
                artist = "Rick Astley",
                title = "Never Gonna Give You Up",
            )
        }

        test("defaults are applied on deserialization") {
            val json = """
                {
                    "type": "music-video",
                    "artist": "Test",
                    "title": "Test Title"
                }
            """
            val dto = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            val mv = dto as ResolvedMetadataDto.MusicVideo
            mv.releaseDate shouldBe null
            mv.tags shouldBe emptyList()
            mv.comment shouldBe null
        }
    }

    context("SeriesEpisode") {
        test("serializes with all fields") {
            val dto = ResolvedMetadataDto.SeriesEpisode(
                seriesName = "Breaking Bad",
                season = "S05",
                episode = "E16",
                title = "Felina",
                tags = listOf("drama"),
            )
            val json = apiJson.encodeToString(ResolvedMetadataDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "series-episode",
                    "seriesName": "Breaking Bad",
                    "season": "S05",
                    "episode": "E16",
                    "title": "Felina",
                    "releaseDate": null,
                    "tags": ["drama"],
                    "comment": null
                }
            """
        }

        test("deserializes with optional fields missing") {
            val json = """
                {
                    "type": "series-episode",
                    "seriesName": "Breaking Bad",
                    "title": "Pilot"
                }
            """
            val dto = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            val se = dto as ResolvedMetadataDto.SeriesEpisode
            se.seriesName shouldBe "Breaking Bad"
            se.title shouldBe "Pilot"
            se.season shouldBe null
            se.episode shouldBe null
        }
    }

    context("Other") {
        test("serializes with minimal fields") {
            val dto = ResolvedMetadataDto.Other(title = "Random Video")
            val json = apiJson.encodeToString(ResolvedMetadataDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "other",
                    "title": "Random Video",
                    "releaseDate": null,
                    "tags": [],
                    "comment": null
                }
            """
        }

        test("deserializes from json") {
            val json = """
                {
                    "type": "other",
                    "title": "Some video",
                    "tags": ["misc", "test"]
                }
            """
            val dto = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            dto shouldBe ResolvedMetadataDto.Other(
                title = "Some video",
                tags = listOf("misc", "test"),
            )
        }
    }

    context("round-trip") {
        test("MusicVideo survives encode-decode") {
            val original = ResolvedMetadataDto.MusicVideo(
                artist = "Daft Punk",
                title = "Get Lucky",
                releaseDate = "2013-04-19",
                tags = listOf("electronic", "funk"),
                comment = "Grammy winner",
            )
            val json = apiJson.encodeToString(ResolvedMetadataDto.serializer(), original)
            val decoded = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            decoded shouldBe original
        }

        test("SeriesEpisode survives encode-decode") {
            val original = ResolvedMetadataDto.SeriesEpisode(
                seriesName = "The Wire",
                season = "S01",
                episode = "E01",
                title = "The Target",
            )
            val json = apiJson.encodeToString(ResolvedMetadataDto.serializer(), original)
            val decoded = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            decoded shouldBe original
        }

        test("Other survives encode-decode") {
            val original = ResolvedMetadataDto.Other(title = "Test", tags = listOf("a", "b"))
            val json = apiJson.encodeToString(ResolvedMetadataDto.serializer(), original)
            val decoded = apiJson.decodeFromString(ResolvedMetadataDto.serializer(), json)
            decoded shouldBe original
        }
    }
})

