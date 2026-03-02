package io.github.alelk.tgvd.domain.workspace

import io.github.alelk.tgvd.domain.common.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Arb.Companion.workspace(
    id: Arb<WorkspaceId> = Arb.workspaceId(),
    name: Arb<String> = Arb.string(3..25, Codepoint.az()),
): Arb<Workspace> = arbitrary {
    Workspace(id = id.bind(), name = name.bind(), createdAt = Clock.System.now())
}



