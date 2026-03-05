package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az

fun Arb.Companion.tag(): Arb<Tag> = arbitrary {
    val len = Arb.int(3..20).bind()
    val value = Arb.string(len..len, Codepoint.az()).bind()
    Tag(value)
}

