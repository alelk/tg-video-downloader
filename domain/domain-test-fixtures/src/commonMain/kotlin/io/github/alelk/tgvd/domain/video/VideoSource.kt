package io.github.alelk.tgvd.domain.video

import io.github.alelk.tgvd.domain.common.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary

fun Arb.Companion.videoSource(
    url: Arb<Url> = Arb.url(),
    videoId: Arb<VideoId> = Arb.videoId(),
    extractor: Arb<Extractor> = Arb.extractor(),
): Arb<VideoSource> = arbitrary {
    VideoSource(url = url.bind(), videoId = videoId.bind(), extractor = extractor.bind())
}

