package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.filePath(): Arb<FilePath> = arbitrary {
    val dir = Arb.element("videos", "music", "downloads", "media").bind()
    val name = Arb.string(3..15, Codepoint.az()).bind()
    val ext = Arb.element("mp4", "mkv", "webm", "mp3").bind()
    FilePath("/$dir/$name.$ext")
}
