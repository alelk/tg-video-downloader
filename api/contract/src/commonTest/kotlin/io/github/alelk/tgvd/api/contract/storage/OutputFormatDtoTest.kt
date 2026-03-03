package io.github.alelk.tgvd.api.contract.storage

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OutputFormatDtoTest : FunSpec({

    context("OriginalVideo") {
        test("serializes as plain string") {
            val dto: OutputFormatDto = OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM)
            val json = apiJson.encodeToString(OutputFormatDto.serializer(), dto)
            json shouldBe "\"original/webm\""
        }

        test("deserializes from plain string") {
            val json = "\"original/mp4\""
            val dto = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
            dto shouldBe OutputFormatDto.OriginalVideo(MediaContainerDto.MP4)
        }

        test("all containers round-trip") {
            MediaContainerDto.entries.forEach { container ->
                val original = OutputFormatDto.OriginalVideo(container)
                val json = apiJson.encodeToString(OutputFormatDto.serializer(), original)
                val decoded = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
                decoded shouldBe original
            }
        }
    }

    context("ConvertedVideo") {
        test("serializes as plain string") {
            val dto: OutputFormatDto = OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4)
            val json = apiJson.encodeToString(OutputFormatDto.serializer(), dto)
            json shouldBe "\"video/mp4\""
        }

        test("deserializes from plain string") {
            val json = "\"video/mkv\""
            val dto = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
            dto shouldBe OutputFormatDto.ConvertedVideo(MediaContainerDto.MKV)
        }
    }

    context("Audio") {
        test("serializes as plain string") {
            val dto: OutputFormatDto = OutputFormatDto.Audio(AudioFormatDto.M4A)
            val json = apiJson.encodeToString(OutputFormatDto.serializer(), dto)
            json shouldBe "\"audio/m4a\""
        }

        test("deserializes from plain string") {
            val json = "\"audio/flac\""
            val dto = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
            dto shouldBe OutputFormatDto.Audio(AudioFormatDto.FLAC)
        }

        test("all audio formats round-trip") {
            AudioFormatDto.entries.forEach { format ->
                val original = OutputFormatDto.Audio(format)
                val json = apiJson.encodeToString(OutputFormatDto.serializer(), original)
                val decoded = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
                decoded shouldBe original
            }
        }
    }

    context("Thumbnail") {
        test("serializes as plain string") {
            val dto: OutputFormatDto = OutputFormatDto.Thumbnail(ImageFormatDto.JPG)
            val json = apiJson.encodeToString(OutputFormatDto.serializer(), dto)
            json shouldBe "\"image/jpg\""
        }

        test("deserializes from plain string") {
            val json = "\"image/webp\""
            val dto = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
            dto shouldBe OutputFormatDto.Thumbnail(ImageFormatDto.WEBP)
        }
    }

    context("inside DTO") {
        test("OutputTargetDto serializes format as string field") {
            val dto = OutputTargetDto(
                path = "/tmp/video.webm",
                format = OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM),
            )
            val json = apiJson.encodeToString(OutputTargetDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "path": "/tmp/video.webm",
                    "format": "original/webm",
                    "embedThumbnail": false,
                    "embedMetadata": false,
                    "embedSubtitles": false,
                    "normalizeAudio": false
                }
            """
        }

        test("OutputTargetDto deserializes format from string field") {
            val json = """
                {
                    "path": "/tmp/converted.mp4",
                    "format": "video/mp4"
                }
            """
            val dto = apiJson.decodeFromString(OutputTargetDto.serializer(), json)
            dto.format.shouldBeInstanceOf<OutputFormatDto.ConvertedVideo>()
            dto.format shouldBe OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4)
        }
    }

    context("allFormats") {
        test("contains all possible combinations") {
            val all = OutputFormatDto.allFormats
            all.size shouldBe (5 + 5 + 5 + 3) // 5 original + 5 video + 5 audio + 3 image
        }

        test("each format round-trips") {
            OutputFormatDto.allFormats.forEach { format ->
                val json = apiJson.encodeToString(OutputFormatDto.serializer(), format)
                val decoded = apiJson.decodeFromString(OutputFormatDto.serializer(), json)
                decoded shouldBe format
            }
        }
    }
})

