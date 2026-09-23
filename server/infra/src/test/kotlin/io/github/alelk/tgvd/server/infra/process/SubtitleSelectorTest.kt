package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SubtitleSelectorTest : FunSpec({
    test("explicit empty subtitle selection disables subtitles") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(writeSubs = true, writeAutoSubs = true, embedSubs = true),
            DownloadPolicy(downloadSubtitles = true, subtitleLanguages = listOf("ru")),
            emptyList(),
        )
        selection.enabled shouldBe false
        selection.arguments() shouldBe listOf("--no-write-subs", "--no-write-auto-subs", "--no-embed-subs")
    }

    test("explicit subtitle selection overrides rule and server languages") {
        SubtitleSelector.select(
            YtDlpConfig(preferredSubtitleLanguages = listOf("ru", "en")),
            DownloadPolicy(downloadSubtitles = true, subtitleLanguages = listOf("ru")),
            listOf("en"),
        ).languages shouldBe listOf("en")
    }

    test("checked subtitle language enables download even when settings disable subtitles") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(writeSubs = false, writeAutoSubs = false),
            DownloadPolicy(),
            listOf("en"),
        )
        selection.arguments().contains("--sub-langs") shouldBe true
        selection.languages shouldBe listOf("en")
    }

    test("downloads regular and generated subtitles for configured languages by default") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(preferredSubtitleLanguages = listOf("RU", "en_US", "ru")),
            DownloadPolicy(),
        )

        selection.writeRegular shouldBe true
        selection.writeAutomatic shouldBe true
        selection.languages shouldBe listOf("ru", "en-us")
        selection.arguments() shouldBe listOf(
            "--write-subs",
            "--write-auto-subs",
            "--no-embed-subs",
            "--sub-langs",
            "ru,en-us",
            "--sleep-subtitles",
            "3",
        )
    }

    test("sleeps between subtitle requests when both regular and automatic captions are requested") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(sleepSubtitles = 4),
            DownloadPolicy(downloadSubtitles = true, subtitleLanguages = listOf("en")),
        )

        selection.arguments() shouldBe listOf(
            "--write-subs",
            "--write-auto-subs",
            "--no-embed-subs",
            "--sub-langs",
            "en",
            "--sleep-subtitles",
            "4",
        )
    }

    test("does not add sleep-subtitles when only one subtitle kind is requested") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(writeSubs = true, writeAutoSubs = false, sleepSubtitles = 4),
            DownloadPolicy(),
        )

        selection.arguments() shouldBe listOf(
            "--write-subs",
            "--no-write-auto-subs",
            "--no-embed-subs",
            "--sub-langs",
            "ru,en",
        )
    }

    test("rule languages override global languages") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(preferredSubtitleLanguages = listOf("ru", "en")),
            DownloadPolicy(downloadSubtitles = true, subtitleLanguages = listOf("DE", "fr")),
        )

        selection.languages shouldBe listOf("de", "fr")
    }

    test("rule enables regular and generated subtitles when global switches are off") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(writeSubs = false, writeAutoSubs = false),
            DownloadPolicy(downloadSubtitles = true, subtitleLanguages = listOf("ru")),
        )

        selection.writeRegular shouldBe true
        selection.writeAutomatic shouldBe true
    }

    test("does not enable subtitles without either global or rule setting") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(writeSubs = false, writeAutoSubs = false),
            DownloadPolicy(),
        )

        selection.enabled shouldBe false
        selection.arguments() shouldBe listOf("--no-write-subs", "--no-write-auto-subs", "--no-embed-subs")
    }

    test("no configured languages disables subtitle downloads explicitly") {
        SubtitleSelector.select(
            YtDlpConfig(preferredSubtitleLanguages = emptyList(), embedSubs = true),
            DownloadPolicy(),
        ).arguments() shouldBe listOf("--no-write-subs", "--no-write-auto-subs", "--no-embed-subs")
    }

    test("legacy comma-separated languages remain supported") {
        val selection = SubtitleSelector.select(
            YtDlpConfig(subLangs = " uk, EN_us,uk "),
            DownloadPolicy(),
        )

        selection.languages shouldBe listOf("uk", "en-us")
    }
})
