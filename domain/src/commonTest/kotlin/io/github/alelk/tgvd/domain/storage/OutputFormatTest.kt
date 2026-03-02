package io.github.alelk.tgvd.domain.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll

class OutputFormatTest : FunSpec({

    context("serialized / parse round-trip") {
        test("OriginalVideo round-trips") {
            MediaContainer.entries.forEach { container ->
                val format = OutputFormat.OriginalVideo(container)
                OutputFormat.parse(format.serialized) shouldBe format
            }
        }

        test("ConvertedVideo round-trips") {
            MediaContainer.entries.forEach { container ->
                val format = OutputFormat.ConvertedVideo(container)
                OutputFormat.parse(format.serialized) shouldBe format
            }
        }

        test("Audio round-trips") {
            AudioFormat.entries.forEach { af ->
                val format = OutputFormat.Audio(af)
                OutputFormat.parse(format.serialized) shouldBe format
            }
        }

        test("Thumbnail round-trips") {
            ImageFormat.entries.forEach { imf ->
                val format = OutputFormat.Thumbnail(imf)
                OutputFormat.parse(format.serialized) shouldBe format
            }
        }

        test("property: any generated OutputFormat survives round-trip") {
            checkAll(Arb.outputFormat()) { format ->
                OutputFormat.parse(format.serialized) shouldBe format
            }
        }
    }

    context("parse errors") {
        test("throws on missing slash") {
            shouldThrow<IllegalArgumentException> {
                OutputFormat.parse("invalid")
            }
        }

        test("throws on unknown kind") {
            shouldThrow<IllegalStateException> {
                OutputFormat.parse("unknown/mp4")
            }
        }

        test("throws on unknown container extension") {
            shouldThrow<IllegalStateException> {
                OutputFormat.parse("original/xyz")
            }
        }

        test("throws on unknown audio extension") {
            shouldThrow<IllegalStateException> {
                OutputFormat.parse("audio/xyz")
            }
        }
    }

    context("extension") {
        test("OriginalVideo delegates to container") {
            OutputFormat.OriginalVideo(MediaContainer.MP4).extension shouldBe "mp4"
            OutputFormat.OriginalVideo(MediaContainer.MKV).extension shouldBe "mkv"
        }

        test("Audio delegates to audio format") {
            OutputFormat.Audio(AudioFormat.MP3).extension shouldBe "mp3"
            OutputFormat.Audio(AudioFormat.FLAC).extension shouldBe "flac"
        }
    }
})

