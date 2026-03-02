package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element

fun Arb.Companion.extractor(): Arb<Extractor> =
    Arb.element(Extractor.YOUTUBE, Extractor.RUTUBE, Extractor.VK, Extractor.GENERIC)

