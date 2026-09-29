package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug

/**
 * Membership check shared by every workspace-scoped use-case.
 *
 * Call it inside the use-case's transaction: the workspace and the membership are read from the
 * same snapshot as the data the use-case goes on to touch.
 */
class WorkspaceAccess(private val workspaceRepository: WorkspaceRepository) {
    /**
     * Resolves the workspace by [slug] and checks that [userId] is a member of it.
     *
     * @return the workspace, [DomainError.WorkspaceNotFoundBySlug] for an unknown slug, or
     *   [DomainError.WorkspaceAccessDenied] when the user is not a member.
     */
    suspend fun requireMember(slug: WorkspaceSlug, userId: TelegramUserId): Either<DomainError, Workspace> = either {
        val workspace = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
        ensure(workspaceRepository.isMember(workspace.id, userId)) {
            DomainError.WorkspaceAccessDenied(workspace.id, userId)
        }
        workspace
    }
}
