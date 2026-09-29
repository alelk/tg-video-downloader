package io.github.alelk.tgvd.domain.preview

import io.github.alelk.tgvd.domain.fixtures.aVideoFormat
import io.github.alelk.tgvd.domain.fixtures.aVideoInfo
import io.github.alelk.tgvd.domain.fixtures.anAudioFormat
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.track.aTrackSelectionSettings
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DefaultMediaSelectionTest :
    FunSpec({
        fun subtitles(vararg languages: String) = languages.map { VideoInfo.SubtitleTrack(it, automatic = false) }

        test("audio: the automatic selection; subtitles: offered languages matching the wanted ones") {
            val video =
                aVideoInfo(
                    availableFormats =
                    listOf(
                        aVideoFormat("137"),
                        anAudioFormat("a-de", language = "de").copy(isOriginalAudio = true),
                        anAudioFormat("a-ru", language = "ru"),
                    ),
                    subtitleTracks = subtitles("EN-gb", "en", "ru", "ru", "fr"),
                )
            val settings =
                aTrackSelectionSettings(
                    preferredAudioLanguages = listOf("ru"),
                    preferredSubtitleLanguages = listOf("en", "ru"),
                )

            defaultMediaSelection(video, DownloadPolicy(), settings) shouldBe
                MediaSelection(audioFormatIds = listOf("a-de", "a-ru"), subtitleLanguages = listOf("EN-gb", "en", "ru"))
        }

        test("no separate audio tracks: audioFormatIds is null") {
            val video =
                aVideoInfo(availableFormats = listOf(VideoInfo.Format("18", "mp4", vcodec = "avc1", acodec = "mp4a")))
            defaultMediaSelection(video, DownloadPolicy(), aTrackSelectionSettings()).audioFormatIds shouldBe null
        }

        test("subtitles switched off by the policy: an empty language list") {
            val video = aVideoInfo(subtitleTracks = subtitles("en", "ru"))
            defaultMediaSelection(video, DownloadPolicy(downloadSubtitles = false), aTrackSelectionSettings())
                .subtitleLanguages shouldBe emptyList()
        }

        test("a wanted language does not match a longer language sharing its prefix") {
            val video = aVideoInfo(subtitleTracks = subtitles("english", "en"))
            val settings = aTrackSelectionSettings(preferredSubtitleLanguages = listOf("en"))
            defaultMediaSelection(video, DownloadPolicy(), settings).subtitleLanguages shouldBe listOf("en")
        }
    })
