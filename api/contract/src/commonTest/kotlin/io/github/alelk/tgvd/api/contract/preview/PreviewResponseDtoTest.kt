package io.github.alelk.tgvd.api.contract.preview

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PreviewResponseDtoTest : FunSpec({

    context("PreviewResponseDto") {
        test("serializes complete response with matched rule") {
            val dto = PreviewResponseDto(
                source = VideoSourceDto("https://youtube.com/watch?v=abc", "abc", "youtube"),
                videoInfo = VideoInfoDto(
                    videoId = "abc",
                    extractor = "youtube",
                    title = "Test Video",
                    channelId = "UC1",
                    channelName = "Channel",
                    durationSeconds = 300,
                    webpageUrl = "https://youtube.com/watch?v=abc",
                ),
                matchedRule = RuleSummaryDto(id = "rule-1", name = "YouTube Downloads"),
                metadataSource = MetadataSourceDto.RULE,
                category = CategoryDto.OTHER,
                metadata = ResolvedMetadataDto.Other(title = "Test Video"),
                storagePlan = StoragePlanDto(
                    original = OutputTargetDto("/tmp/test.webm", OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM)),
                ),
                warnings = listOf("Low quality source"),
            )
            val json = apiJson.encodeToString(PreviewResponseDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "source": {
                        "url": "https://youtube.com/watch?v=abc",
                        "videoId": "abc",
                        "extractor": "youtube"
                    },
                    "videoInfo": {
                        "videoId": "abc",
                        "extractor": "youtube",
                        "title": "Test Video",
                        "channelId": "UC1",
                        "channelName": "Channel",
                        "uploadDate": null,
                        "durationSeconds": 300,
                        "webpageUrl": "https://youtube.com/watch?v=abc",
                        "thumbnails": [],
                        "description": null
                    },
                    "matchedRule": {
                        "id": "rule-1",
                        "name": "YouTube Downloads"
                    },
                    "metadataSource": "rule",
                    "category": "other",
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
                            "format": "original/webm",
                            "embedThumbnail": false,
                            "embedMetadata": false,
                            "embedSubtitles": false,
                            "normalizeAudio": false
                        },
                        "additional": []
                    },
                    "warnings": ["Low quality source"]
                }
            """
        }

        test("deserializes without matched rule") {
            val json = """
                {
                    "source": {
                        "url": "https://example.com/video",
                        "videoId": "v1",
                        "extractor": "generic"
                    },
                    "videoInfo": {
                        "videoId": "v1",
                        "extractor": "generic",
                        "title": "Some Video",
                        "channelId": "ch1",
                        "channelName": "Channel",
                        "durationSeconds": 60,
                        "webpageUrl": "https://example.com/video"
                    },
                    "metadataSource": "fallback",
                    "category": "other",
                    "metadata": {
                        "type": "other",
                        "title": "Some Video"
                    },
                    "storagePlan": {
                        "original": {
                            "path": "/tmp/video.webm",
                            "format": "original/webm"
                        }
                    }
                }
            """
            val dto = apiJson.decodeFromString(PreviewResponseDto.serializer(), json)
            dto.matchedRule shouldBe null
            dto.metadataSource shouldBe MetadataSourceDto.FALLBACK
            dto.category shouldBe CategoryDto.OTHER
            dto.warnings shouldBe emptyList()
        }

        test("round-trip with music-video metadata") {
            val original = PreviewResponseDto(
                source = VideoSourceDto("https://youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ", "youtube"),
                videoInfo = VideoInfoDto(
                    videoId = "dQw4w9WgXcQ",
                    extractor = "youtube",
                    title = "Rick Astley - Never Gonna Give You Up",
                    channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
                    channelName = "Rick Astley",
                    uploadDate = "2009-10-25",
                    durationSeconds = 213,
                    webpageUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
                ),
                matchedRule = RuleSummaryDto("rule-music", "Music Videos"),
                metadataSource = MetadataSourceDto.RULE,
                category = CategoryDto.MUSIC_VIDEO,
                metadata = ResolvedMetadataDto.MusicVideo(
                    artist = "Rick Astley",
                    title = "Never Gonna Give You Up",
                    tags = listOf("pop", "80s"),
                ),
                storagePlan = StoragePlanDto(
                    original = OutputTargetDto(
                        "/media/original/Rick Astley/Never Gonna Give You Up.webm",
                        OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM),
                    ),
                    additional = listOf(
                        OutputTargetDto(
                            "/media/converted/Rick Astley/Never Gonna Give You Up.mp4",
                            OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4),
                        ),
                    ),
                ),
            )
            val json = apiJson.encodeToString(PreviewResponseDto.serializer(), original)
            val decoded = apiJson.decodeFromString(PreviewResponseDto.serializer(), json)
            decoded shouldBe original
        }
    }
})
