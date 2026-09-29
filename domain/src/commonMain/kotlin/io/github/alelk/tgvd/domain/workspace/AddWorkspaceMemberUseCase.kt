package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class AddWorkspaceMemberUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    /**
     * Adds [targetUserId] to the workspace [workspaceSlug] with [role]. Only its OWNER may do this;
     * anyone else (a member or a stranger) gets [DomainError.WorkspaceAccessDenied].
     */
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        targetUserId: TelegramUserId,
        role: WorkspaceRole,
    ): Either<DomainError, WorkspaceMember> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceRepository.requireOwner(workspaceSlug, actor).bind()
            workspaceRepository.addMember(
                WorkspaceMember(
                    workspaceId = workspace.id,
                    userId = targetUserId,
                    role = role,
                    joinedAt = clock.now(),
                ),
            ).bind()
        }
    }
}
