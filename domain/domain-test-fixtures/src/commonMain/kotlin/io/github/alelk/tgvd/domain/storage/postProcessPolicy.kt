package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean

fun Arb.Companion.postProcessPolicy(
    embedThumbnail: Arb<Boolean> = Arb.boolean(),
    embedMetadata: Arb<Boolean> = Arb.boolean(),
    normalizeAudio: Arb<Boolean> = Arb.boolean(),
): Arb<PostProcessPolicy> = arbitrary {
    PostProcessPolicy(
        embedThumbnail = embedThumbnail.bind(),
        embedMetadata = embedMetadata.bind(),
        normalizeAudio = normalizeAudio.bind(),
    )
}

