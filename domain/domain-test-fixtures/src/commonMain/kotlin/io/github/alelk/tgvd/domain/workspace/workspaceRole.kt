package io.github.alelk.tgvd.domain.workspace

import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum

fun Arb.Companion.workspaceRole(): Arb<WorkspaceRole> = Arb.enum<WorkspaceRole>()