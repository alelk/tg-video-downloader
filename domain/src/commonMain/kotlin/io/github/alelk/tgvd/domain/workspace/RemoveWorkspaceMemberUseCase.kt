package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner

class RemoveWorkspaceMemberUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
) {
    /**
     * Removes [targetUserId] from the workspace [workspaceSlug]. Only its OWNER may do this; anyone else
     * gets [DomainError.WorkspaceAccessDenied]. A user who is not a member is a [DomainError.ValidationError].
     */
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        targetUserId: TelegramUserId,
    ): Either<DomainError, Unit> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceRepository.requireOwner(workspaceSlug, actor).bind()
            if (!workspaceRepository.removeMember(workspace.id, targetUserId)) {
                raise(DomainError.ValidationError("userId", "User ${targetUserId.value} is not a member"))
            }
        }
    }
}
