package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.mediaContainer(): Arb<MediaContainer> = Arb.enum<MediaContainer>()

fun Arb.Companion.audioFormat(): Arb<AudioFormat> = Arb.enum<AudioFormat>()

fun Arb.Companion.imageFormat(): Arb<ImageFormat> = Arb.enum<ImageFormat>()

fun Arb.Companion.outputFormat(): Arb<OutputFormat> = arbitrary {
    when (Arb.int(0..3).bind()) {
        0 -> OutputFormat.OriginalVideo(Arb.mediaContainer().bind())
        1 -> OutputFormat.ConvertedVideo(Arb.mediaContainer().bind())
        2 -> OutputFormat.Audio(Arb.audioFormat().bind())
        else -> OutputFormat.Thumbnail(Arb.imageFormat().bind())
    }
}

