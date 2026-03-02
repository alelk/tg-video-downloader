package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.Codepoint

fun Arb.Companion.videoId(
    value: Arb<String> = Arb.string(1..20, Codepoint.az()),
): Arb<VideoId> = arbitrary { VideoId(value.bind()) }

