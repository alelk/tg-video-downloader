package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug

/**
 * The workspace [slug] if [userId] is its OWNER: [DomainError.WorkspaceNotFoundBySlug] for an unknown
 * slug, [DomainError.WorkspaceAccessDenied] for a plain member or a non-member.
 */
internal suspend fun WorkspaceRepository.requireOwner(
    slug: WorkspaceSlug,
    userId: TelegramUserId,
): Either<DomainError, Workspace> = either {
    val workspace = findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
    val membership = findMembers(workspace.id).find { it.userId == userId }
    ensure(membership?.role == WorkspaceRole.OWNER) { DomainError.WorkspaceAccessDenied(workspace.id, userId) }
    workspace
}
