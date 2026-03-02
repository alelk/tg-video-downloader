package io.github.alelk.tgvd.domain.workspace

import io.github.alelk.tgvd.domain.common.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Arb.Companion.workspaceMember(
    workspaceId: Arb<WorkspaceId> = Arb.workspaceId(),
    userId: Arb<TelegramUserId> = Arb.telegramUserId(),
    role: Arb<WorkspaceRole> = Arb.workspaceRole(),
): Arb<WorkspaceMember> = arbitrary {
    WorkspaceMember(
        workspaceId = workspaceId.bind(),
        userId = userId.bind(),
        role = role.bind(),
        joinedAt = Clock.System.now(),
    )
}