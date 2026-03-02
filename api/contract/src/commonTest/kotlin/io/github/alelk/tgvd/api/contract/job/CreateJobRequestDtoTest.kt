package io.github.alelk.tgvd.api.contract.job

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CreateJobRequestDtoTest : FunSpec({

    context("CreateJobRequestDto") {
        test("serializes complete request") {
            val dto = CreateJobRequestDto(
                source = VideoSourceDto(
                    url = "https://youtube.com/watch?v=abc123",
                    videoId = "abc123",
                    extractor = "youtube",
                ),
                ruleId = "rule-1",
                category = "other",
                videoInfo = VideoInfoDto(
                    videoId = "abc123",
                    extractor = "youtube",
                    title = "Test Video",
                    channelId = "UC123",
                    channelName = "TestChannel",
                    durationSeconds = 120,
                    webpageUrl = "https://youtube.com/watch?v=abc123",
                ),
                metadata = ResolvedMetadataDto.Other(title = "Test Video"),
                storagePlan = StoragePlanDto(
                    original = OutputTargetDto("/tmp/test.webm", "original/webm"),
                ),
            )
            val json = apiJson.encodeToString(CreateJobRequestDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "source": {
                        "url": "https://youtube.com/watch?v=abc123",
                        "videoId": "abc123",
                        "extractor": "youtube"
                    },
                    "ruleId": "rule-1",
                    "category": "other",
                    "videoInfo": {
                        "videoId": "abc123",
                        "extractor": "youtube",
                        "title": "Test Video",
                        "channelId": "UC123",
                        "channelName": "TestChannel",
                        "uploadDate": null,
                        "durationSeconds": 120,
                        "webpageUrl": "https://youtube.com/watch?v=abc123",
                        "thumbnails": [],
                        "description": null
                    },
                    "metadata": {
                        "type": "other",
                        "title": "Test Video",
                        "releaseDate": null,
                        "tags": [],
                        "comment": null
                    },
                    "storagePlan": {
                        "original": {
                            "path": "/tmp/test.webm",
                            "format": "original/webm"
                        },
                        "additional": []
                    },
                    "saveAsRule": null
                }
            """
        }

        test("deserializes without optional fields") {
            val json = """
                {
                    "source": {
                        "url": "https://youtube.com/watch?v=x",
                        "videoId": "x",
                        "extractor": "youtube"
                    },
                    "category": "music-video",
                    "videoInfo": {
                        "videoId": "x",
                        "extractor": "youtube",
                        "title": "Song",
                        "channelId": "UC1",
                        "channelName": "Artist",
                        "durationSeconds": 200,
                        "webpageUrl": "https://youtube.com/watch?v=x"
                    },
                    "metadata": {
                        "type": "music-video",
                        "artist": "Artist",
                        "title": "Song"
                    },
                    "storagePlan": {
                        "original": {
                            "path": "/tmp/song.webm",
                            "format": "original/webm"
                        }
                    }
                }
            """
            val dto = apiJson.decodeFromString(CreateJobRequestDto.serializer(), json)
            dto.ruleId shouldBe null
            dto.saveAsRule shouldBe null
            dto.category shouldBe "music-video"
            dto.source.videoId shouldBe "x"
        }

        test("round-trip with saveAsRule") {
            val original = CreateJobRequestDto(
                source = VideoSourceDto("https://example.com", "v1", "generic"),
                category = "other",
                videoInfo = VideoInfoDto(
                    videoId = "v1",
                    extractor = "generic",
                    title = "Test",
                    channelId = "ch1",
                    channelName = "Chan",
                    durationSeconds = 60,
                    webpageUrl = "https://example.com",
                ),
                metadata = ResolvedMetadataDto.Other(title = "Test"),
                storagePlan = StoragePlanDto(
                    original = OutputTargetDto("/tmp/test.webm", "original/webm"),
                ),
                saveAsRule = SaveAsRuleDto(
                    enabled = true,
                    matchBy = "channelId",
                    includeCategory = true,
                    includeMetadataTemplate = false,
                    includeStoragePolicy = true,
                ),
            )
            val json = apiJson.encodeToString(CreateJobRequestDto.serializer(), original)
            val decoded = apiJson.decodeFromString(CreateJobRequestDto.serializer(), json)
            decoded shouldBe original
        }
    }
})

