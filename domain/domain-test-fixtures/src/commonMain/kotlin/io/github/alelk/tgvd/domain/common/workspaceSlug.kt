package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string

fun Arb.Companion.workspaceSlug(): Arb<WorkspaceSlug> = arbitrary {
    val len = Arb.int(1..16).bind()
    val suffix = Arb.string(len, Codepoint.az()).bind()
    WorkspaceSlug("ws-$suffix-1")
}




