package io.github.alelk.tgvd.domain.storage

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll

class TrackPreferencesTest : FunSpec({

    context("overriddenBy") {
        test("null override keeps the base") {
            checkAll(Arb.trackPreferences()) { base ->
                base.overriddenBy(null) shouldBe base
            }
        }

        test("empty override keeps the base") {
            checkAll(Arb.trackPreferences()) { base ->
                base.overriddenBy(TrackPreferences()) shouldBe base
            }
        }

        test("set fields of the override win, unset fields are inherited") {
            val base = TrackPreferences(audioLanguages = listOf("en"), downloadSubtitles = true, subtitleLanguages = listOf("ru"))
            val override = TrackPreferences(audioLanguages = emptyList())
            base.overriddenBy(override) shouldBe
                TrackPreferences(audioLanguages = emptyList(), downloadSubtitles = true, subtitleLanguages = listOf("ru"))
        }

        test("empty subtitle language list inherits") {
            val base = TrackPreferences(subtitleLanguages = listOf("ru"))
            base.overriddenBy(TrackPreferences(subtitleLanguages = emptyList())).subtitleLanguages shouldBe listOf("ru")
        }
    }

    context("DownloadPolicy.withOverrides") {
        test("channel overrides win over the rule") {
            val rule = DownloadPolicy(
                maxQuality = DownloadPolicy.VideoQuality.HD_720,
                downloadSubtitles = true,
                subtitleLanguages = listOf("en"),
                audioLanguages = listOf("ru"),
            )
            val channel = TrackPreferences(downloadSubtitles = false, audioLanguages = listOf("de", "fr"))
            rule.withOverrides(channel) shouldBe rule.copy(downloadSubtitles = false, audioLanguages = listOf("de", "fr"))
        }

        test("unset channel fields inherit the rule, unset rule fields stay unset (global)") {
            val rule = DownloadPolicy(subtitleLanguages = listOf("en"))
            val channel = TrackPreferences(subtitleLanguages = listOf("ru"))
            val merged = rule.withOverrides(channel)
            merged.subtitleLanguages shouldBe listOf("ru")
            merged.downloadSubtitles shouldBe null
            merged.audioLanguages shouldBe null
        }

        test("null or empty overrides keep the policy") {
            checkAll(Arb.downloadPolicy()) { policy ->
                policy.withOverrides(null) shouldBe policy
                policy.withOverrides(TrackPreferences()) shouldBe policy
            }
        }
    }
})
