package io.github.alelk.tgvd.domain.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TagTest : FunSpec({

    test("valid tags") {
        Tag("music-video").value shouldBe "music-video"
        Tag("lofi").value shouldBe "lofi"
        Tag("a").value shouldBe "a"
        Tag("tech-review").value shouldBe "tech-review"
        Tag("pop123").value shouldBe "pop123"
        Tag("1rock").value shouldBe "1rock"
    }

    test("rejects blank tag") {
        shouldThrow<IllegalArgumentException> {
            Tag("")
        }
        shouldThrow<IllegalArgumentException> {
            Tag("   ")
        }
    }

    test("rejects uppercase") {
        shouldThrow<IllegalArgumentException> {
            Tag("Music-Video")
        }
    }

    test("rejects spaces") {
        shouldThrow<IllegalArgumentException> {
            Tag("music video")
        }
    }

    test("rejects starting with hyphen") {
        shouldThrow<IllegalArgumentException> {
            Tag("-music")
        }
    }

    test("rejects ending with hyphen") {
        shouldThrow<IllegalArgumentException> {
            Tag("music-")
        }
    }

    test("rejects special characters") {
        shouldThrow<IllegalArgumentException> {
            Tag("music_video")
        }
        shouldThrow<IllegalArgumentException> {
            Tag("music.video")
        }
    }

    test("rejects too long tag") {
        shouldThrow<IllegalArgumentException> {
            Tag("a".repeat(51))
        }
    }

    test("accepts max length tag") {
        val tag = Tag("a".repeat(50))
        tag.value.length shouldBe 50
    }
})

