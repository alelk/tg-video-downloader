package io.github.alelk.tgvd.domain.workspace

import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import kotlin.time.Instant

/**
 * Связь пользователя с workspace.
 */
data class WorkspaceMember(
    val workspaceId: WorkspaceId,
    val userId: TelegramUserId,
    val role: WorkspaceRole,
    val joinedAt: Instant,
)

