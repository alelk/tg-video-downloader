package io.github.alelk.tgvd.server.transport.util

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.TelegramUser

/**
 * Resolves a workspace by slug and verifies that [user] is a member.
 * Returns [DomainError.WorkspaceAccessDenied] if not a member.
 */
suspend fun WorkspaceRepository.requireWorkspaceMember(
    slug: io.github.alelk.tgvd.domain.common.WorkspaceSlug,
    user: TelegramUser,
): Either<DomainError, Workspace> = either {
    val ws = findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
    ensure(isMember(ws.id, user.id)) { DomainError.WorkspaceAccessDenied(ws.id, user.id) }
    ws
}

