package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.url(): Arb<Url> = arbitrary {
    val scheme = Arb.element("https://", "http://").bind()
    val host = Arb.string(3..20, Codepoint.az()).bind()
    val path = Arb.string(0..15, Codepoint.az()).bind()
    Url("${scheme}${host}.com/${path}")
}

