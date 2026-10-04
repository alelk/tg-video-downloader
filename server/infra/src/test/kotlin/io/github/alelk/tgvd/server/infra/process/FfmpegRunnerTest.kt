package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FfmpegRunnerTest :
    FunSpec({

        test("subtitleCodecFor picks a codec each muxer actually accepts") {
            subtitleCodecFor(MediaContainer.MP4) shouldBe "mov_text"
            subtitleCodecFor(MediaContainer.MOV) shouldBe "mov_text"
            subtitleCodecFor(MediaContainer.MKV) shouldBe "copy"
            subtitleCodecFor(MediaContainer.WEBM) shouldBe "webvtt"
        }

        test("subtitleCodecFor drops subtitles for AVI — no usable soft-subtitle support") {
            subtitleCodecFor(MediaContainer.AVI) shouldBe null
        }
    })
