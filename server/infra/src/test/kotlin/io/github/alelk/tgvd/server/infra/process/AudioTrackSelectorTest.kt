package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AudioTrackSelectorTest : FunSpec({
    fun video(id: String, height: Int = 1080) = VideoInfo.Format(
        formatId = id, extension = "webm", height = height, vcodec = "vp9", acodec = "none",
    )
    fun audio(
        id: String,
        language: String?,
        bitrate: Double,
        original: Boolean = false,
        preference: Int? = null,
    ) = VideoInfo.Format(
        formatId = id,
        extension = "webm",
        vcodec = "none",
        acodec = "opus",
        tbr = bitrate,
        language = language,
        languagePreference = preference,
        isOriginalAudio = original,
    )

    test("keeps Russian original even when English dub has higher bitrate") {
        val selection = AudioTrackSelector.select(
            formats = listOf(video("v"), audio("ru-low", "ru", 96.0, original = true), audio("en-high", "en", 160.0)),
            quality = DownloadPolicy.VideoQuality.BEST,
            preferredLanguages = listOf("ru", "en"),
            maxAdditionalTracks = 2,
        )

        selection.originalAudio?.formatId shouldBe "ru-low"
        selection.additionalAudio.map { it.formatId }.shouldContainExactly("en-high")
        selection.formatSelector shouldBe "v+ru-low+en-high"
    }

    test("uses language preference as fallback when explicit original marker is absent") {
        val selection = AudioTrackSelector.select(
            listOf(video("v"), audio("en", "en", 160.0, preference = -10), audio("ru", "ru", 96.0, preference = 10)),
            DownloadPolicy.VideoQuality.BEST,
            preferredLanguages = listOf("en"),
            maxAdditionalTracks = 2,
        )

        selection.originalAudio?.formatId shouldBe "ru"
        selection.additionalAudio.map { it.formatId }.shouldContainExactly("en")
    }

    test("matches regional language tags and selects only one best variant") {
        val selection = AudioTrackSelector.select(
            listOf(video("v"), audio("de", "de", 96.0, original = true), audio("en-us", "en-US", 128.0), audio("en-gb", "en-GB", 96.0)),
            DownloadPolicy.VideoQuality.BEST,
            preferredLanguages = listOf("en"),
            maxAdditionalTracks = 2,
        )

        selection.additionalAudio.map { it.formatId }.shouldContainExactly("en-us")
    }

    test("missing optional language does not prevent selection") {
        val selection = AudioTrackSelector.select(
            listOf(video("v"), audio("de", "de", 96.0, original = true)),
            DownloadPolicy.VideoQuality.BEST,
            preferredLanguages = listOf("ru", "en"),
            maxAdditionalTracks = 2,
        )

        selection.additionalAudio shouldBe emptyList()
        selection.formatSelector shouldBe "v+de"
    }

    test("does not replace source-first fallback with a higher bitrate dub") {
        val selection = AudioTrackSelector.select(
            listOf(video("v"), audio("source", null, 64.0), audio("dub", null, 192.0)),
            DownloadPolicy.VideoQuality.BEST,
            preferredLanguages = emptyList(),
            maxAdditionalTracks = 0,
        )

        selection.originalAudio?.formatId shouldBe "source"
    }

    test("respects preferred language order and additional track limit") {
        val selection = AudioTrackSelector.select(
            listOf(video("v"), audio("de", "de", 96.0, original = true), audio("en", "en", 128.0), audio("ru", "ru", 128.0)),
            DownloadPolicy.VideoQuality.BEST,
            preferredLanguages = listOf("ru", "en"),
            maxAdditionalTracks = 1,
        )

        selection.additionalAudio.map { it.formatId }.shouldContainExactly("ru")
    }
})
