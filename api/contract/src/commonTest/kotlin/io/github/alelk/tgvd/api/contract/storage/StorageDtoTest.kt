package io.github.alelk.tgvd.api.contract.storage

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StorageDtoTest : FunSpec({

    context("OutputTargetDto") {
        test("serializes format as string") {
            val dto = OutputTargetDto(
                path = "/tmp/tgvd/media/video.webm",
                format = OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM),
            )
            val json = apiJson.encodeToString(OutputTargetDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "path": "/tmp/tgvd/media/video.webm",
                    "format": "original/webm"
                }
            """
        }

        test("deserializes from json") {
            val json = """
                {
                    "path": "/media/converted/video.mp4",
                    "format": "video/mp4"
                }
            """
            val dto = apiJson.decodeFromString(OutputTargetDto.serializer(), json)
            dto.path shouldBe "/media/converted/video.mp4"
            dto.format shouldBe OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4)
        }
    }

    context("StoragePlanDto") {
        test("serializes with original and additional targets") {
            val dto = StoragePlanDto(
                original = OutputTargetDto("/tmp/original.webm", OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM)),
                additional = listOf(
                    OutputTargetDto("/tmp/converted.mp4", OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4)),
                    OutputTargetDto("/tmp/audio.m4a", OutputFormatDto.Audio(AudioFormatDto.M4A)),
                ),
            )
            val json = apiJson.encodeToString(StoragePlanDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "original": {
                        "path": "/tmp/original.webm",
                        "format": "original/webm"
                    },
                    "additional": [
                        {
                            "path": "/tmp/converted.mp4",
                            "format": "video/mp4"
                        },
                        {
                            "path": "/tmp/audio.m4a",
                            "format": "audio/m4a"
                        }
                    ]
                }
            """
        }

        test("deserializes with empty additional") {
            val json = """
                {
                    "original": {
                        "path": "/tmp/video.webm",
                        "format": "original/webm"
                    }
                }
            """
            val dto = apiJson.decodeFromString(StoragePlanDto.serializer(), json)
            dto.original shouldBe OutputTargetDto("/tmp/video.webm", OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM))
            dto.additional shouldBe emptyList()
        }
    }

    context("DownloadPolicyDto") {
        test("serializes with defaults") {
            val dto = DownloadPolicyDto()
            val json = apiJson.encodeToString(DownloadPolicyDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "maxQuality": "best",
                    "preferredContainer": null,
                    "downloadSubtitles": false,
                    "subtitleLanguages": []
                }
            """
        }

        test("serializes with custom values") {
            val dto = DownloadPolicyDto(
                maxQuality = VideoQualityDto.HD_1080,
                preferredContainer = MediaContainerDto.MP4,
                downloadSubtitles = true,
                subtitleLanguages = listOf("en", "ru"),
            )
            val json = apiJson.encodeToString(DownloadPolicyDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "maxQuality": "hd_1080",
                    "preferredContainer": "mp4",
                    "downloadSubtitles": true,
                    "subtitleLanguages": ["en", "ru"]
                }
            """
        }

        test("round-trip") {
            val original = DownloadPolicyDto(
                maxQuality = VideoQualityDto.HD_720,
                preferredContainer = MediaContainerDto.MKV,
                downloadSubtitles = true,
                subtitleLanguages = listOf("en"),
            )
            val json = apiJson.encodeToString(DownloadPolicyDto.serializer(), original)
            val decoded = apiJson.decodeFromString(DownloadPolicyDto.serializer(), json)
            decoded shouldBe original
        }
    }

    context("StoragePolicyDto") {
        test("serializes with additional outputs") {
            val dto = StoragePolicyDto(
                originalTemplate = "/media/original/{title}.{ext}",
                additionalOutputs = listOf(
                    OutputTemplateDto(
                        pathTemplate = "/media/converted/{title}.mp4",
                        format = OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4),
                    ),
                ),
            )
            val json = apiJson.encodeToString(StoragePolicyDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "originalTemplate": "/media/original/{title}.{ext}",
                    "additionalOutputs": [
                        {
                            "pathTemplate": "/media/converted/{title}.mp4",
                            "format": "video/mp4"
                        }
                    ]
                }
            """
        }

        test("deserializes with empty additional") {
            val json = """
                {
                    "originalTemplate": "/media/{title}.{ext}"
                }
            """
            val dto = apiJson.decodeFromString(StoragePolicyDto.serializer(), json)
            dto.originalTemplate shouldBe "/media/{title}.{ext}"
            dto.additionalOutputs shouldBe emptyList()
        }
    }

    context("PostProcessPolicyDto") {
        test("serializes with defaults") {
            val dto = PostProcessPolicyDto()
            val json = apiJson.encodeToString(PostProcessPolicyDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "embedThumbnail": true,
                    "embedMetadata": true,
                    "normalizeAudio": false
                }
            """
        }

        test("round-trip") {
            val original = PostProcessPolicyDto(
                embedThumbnail = false,
                embedMetadata = true,
                normalizeAudio = true,
            )
            val json = apiJson.encodeToString(PostProcessPolicyDto.serializer(), original)
            val decoded = apiJson.decodeFromString(PostProcessPolicyDto.serializer(), json)
            decoded shouldBe original
        }
    }
})

