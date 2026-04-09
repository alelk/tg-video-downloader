package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.tx.TransactionRunner

class RemoveWorkspaceMemberUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
) {
    /** Removes [targetUserId] from [workspaceId]. Only OWNER may call this. */
    suspend operator fun invoke(
        workspaceId: WorkspaceId,
        callerId: TelegramUserId,
        targetUserId: TelegramUserId,
    ): Either<DomainError, Unit> =
        txRunner.inRwTransaction {
            either {
                val callerMembership = workspaceRepository.findMembers(workspaceId)
                    .find { it.userId == callerId }
                ensure(callerMembership?.role == WorkspaceRole.OWNER) {
                    DomainError.WorkspaceAccessDenied(workspaceId, callerId)
                }
                if (!workspaceRepository.removeMember(workspaceId, targetUserId)) {
                    raise(DomainError.ValidationError("userId", "User ${targetUserId.value} is not a member"))
                }
            }
        }
}

