package io.github.alelk.tgvd.features.download.model

import io.github.alelk.tgvd.api.contract.video.SubtitleTrackDto
import io.github.alelk.tgvd.api.contract.video.VideoFormatDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PreviewMediaOptionsTest : FunSpec({
    fun format(
        id: String,
        height: Int? = null,
        bitrate: Double? = null,
        language: String? = null,
        videoCodec: String? = "none",
        audioCodec: String? = "aac",
    ) = VideoFormatDto(
        formatId = id,
        extension = "m4a",
        height = height,
        tbr = bitrate,
        vcodec = videoCodec,
        acodec = audioCodec,
        language = language,
    )

    test("formats maximum available video quality") {
        maxAvailableQualityLabel(listOf(format("a", height = 720), format("b", height = 2160))) shouldBe "4K"
        maxAvailableQualityLabel(emptyList()) shouldBe null
    }

    test("selects default audio variant for each language") {
        val englishLow = format("en-low", bitrate = 64.0, language = "en")
        val englishHigh = format("en-high", bitrate = 192.0, language = "en")
        val french = format("fr", bitrate = 128.0, language = "fr")

        val result = selectAudioOptions(
            formats = listOf(englishLow, englishHigh, french),
            defaultAudioFormatIds = listOf("en-low"),
        )

        result.shouldContainExactly(englishLow, french)
    }

    test("falls back to highest bitrate and ignores video formats") {
        val low = format("low", bitrate = 64.0, language = "en")
        val high = format("high", bitrate = 256.0, language = "en")
        val video = format("video", bitrate = 5000.0, videoCodec = "h264")

        selectAudioOptions(listOf(low, video, high), emptyList()) shouldBe listOf(high)
    }

    test("groups subtitle tracks by language") {
        val manual = SubtitleTrackDto(language = "en", automatic = false)
        val automatic = SubtitleTrackDto(language = "en", automatic = true)
        val french = SubtitleTrackDto(language = "fr", automatic = false)

        groupSubtitleOptions(listOf(manual, automatic, french)) shouldBe mapOf(
            "en" to listOf(manual, automatic),
            "fr" to listOf(french),
        )
    }
})
