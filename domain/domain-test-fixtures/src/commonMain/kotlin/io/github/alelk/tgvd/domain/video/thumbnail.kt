package io.github.alelk.tgvd.domain.video

import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.url
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull

fun Arb.Companion.thumbnail(
    url: Arb<Url> = Arb.url(),
    width: Arb<Int?> = Arb.int(120..1920).orNull(0.3),
    height: Arb<Int?> = Arb.int(90..1080).orNull(0.3),
): Arb<VideoInfo.Thumbnail> = arbitrary {
    VideoInfo.Thumbnail(url = url.bind(), width = width.bind(), height = height.bind())
}