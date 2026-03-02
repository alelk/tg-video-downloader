package io.github.alelk.tgvd.domain.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll
import io.github.alelk.tgvd.domain.storage.MediaContainer

class ValueClassValidationTest : FunSpec({

    context("VideoId") {
        test("rejects blank value") {
            shouldThrow<IllegalArgumentException> { VideoId("  ") }
        }

        test("rejects too long value") {
            shouldThrow<IllegalArgumentException> { VideoId("a".repeat(65)) }
        }

        test("accepts valid value") {
            VideoId("abc123").value shouldBe "abc123"
        }
    }

    context("ChannelId") {
        test("rejects blank value") {
            shouldThrow<IllegalArgumentException> { ChannelId("  ") }
        }

        test("accepts valid value") {
            ChannelId("UC123").value shouldBe "UC123"
        }
    }

    context("Url") {
        test("rejects blank value") {
            shouldThrow<IllegalArgumentException> { Url("  ") }
        }

        test("rejects url without scheme") {
            shouldThrow<IllegalArgumentException> { Url("youtube.com/watch") }
        }

        test("accepts http url") {
            Url("http://example.com").value shouldBe "http://example.com"
        }

        test("accepts https url") {
            Url("https://example.com").value shouldBe "https://example.com"
        }
    }

    context("TelegramUserId") {
        test("rejects zero") {
            shouldThrow<IllegalArgumentException> { TelegramUserId(0) }
        }

        test("rejects negative") {
            shouldThrow<IllegalArgumentException> { TelegramUserId(-1) }
        }

        test("accepts positive") {
            TelegramUserId(42).value shouldBe 42L
        }
    }

    context("LocalDate") {
        test("rejects non-ISO format") {
            shouldThrow<IllegalArgumentException> { LocalDate("not-a-date") }
        }

        test("rejects dd/mm/yyyy format") {
            shouldThrow<IllegalArgumentException> { LocalDate("15/03/2024") }
        }

        test("accepts ISO format") {
            val d = LocalDate("2024-03-15")
            d.year shouldBe 2024
            d.month shouldBe 3
            d.day shouldBe 15
        }
    }

    context("FilePath") {
        test("rejects blank value") {
            shouldThrow<IllegalArgumentException> { FilePath("  ") }
        }

        test("extracts fileName") {
            FilePath("/media/video.mp4").fileName shouldBe "video.mp4"
        }

        test("extracts parent") {
            FilePath("/media/sub/video.mp4").parent shouldBe "/media/sub"
        }

        test("extracts extension") {
            FilePath("/media/video.mp4").extension shouldBe "mp4"
        }

        test("extension is empty when no dot") {
            FilePath("/media/video").extension shouldBe ""
        }
    }

    context("Extractor") {
        test("rejects blank value") {
            shouldThrow<IllegalArgumentException> { Extractor("  ") }
        }

        test("companion values are correct") {
            Extractor.YOUTUBE.value shouldBe "youtube"
            Extractor.RUTUBE.value shouldBe "rutube"
            Extractor.VK.value shouldBe "vk"
            Extractor.GENERIC.value shouldBe "generic"
        }
    }

    context("Category") {
        test("fromString case-insensitive lookup") {
            Category.fromString("music_video") shouldBe Category.MUSIC_VIDEO
            Category.fromString("MUSIC_VIDEO") shouldBe Category.MUSIC_VIDEO
            Category.fromString("Music_Video") shouldBe Category.MUSIC_VIDEO
        }

        test("fromString returns null for unknown") {
            Category.fromString("nonexistent") shouldBe null
        }

        test("property: fromString(name) round-trips for all entries") {
            checkAll(Arb.category()) { cat ->
                Category.fromString(cat.name) shouldBe cat
            }
        }
    }

    context("MediaContainer") {
        test("fromExtension round-trips for all entries") {
            MediaContainer.entries.forEach { mc ->
                MediaContainer.fromExtension(mc.extension) shouldBe mc
            }
        }

        test("fromExtension is case-insensitive") {
            MediaContainer.fromExtension("MP4") shouldBe MediaContainer.MP4
            MediaContainer.fromExtension("Mkv") shouldBe MediaContainer.MKV
        }

        test("fromExtension returns null for unknown") {
            MediaContainer.fromExtension("xyz") shouldBe null
        }
    }
})


