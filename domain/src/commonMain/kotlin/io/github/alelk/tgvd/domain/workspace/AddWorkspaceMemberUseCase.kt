package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class AddWorkspaceMemberUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    /** Adds [targetUserId] to [workspaceId] with [role]. Only OWNER may call this. */
    suspend operator fun invoke(
        workspaceId: WorkspaceId,
        callerId: TelegramUserId,
        targetUserId: TelegramUserId,
        role: WorkspaceRole,
    ): Either<DomainError, WorkspaceMember> =
        txRunner.inRwTransaction {
            either {
                val callerMembership =
                    workspaceRepository.findMembers(workspaceId)
                        .find { it.userId == callerId }
                ensure(callerMembership?.role == WorkspaceRole.OWNER) {
                    DomainError.WorkspaceAccessDenied(workspaceId, callerId)
                }
                workspaceRepository.addMember(
                    WorkspaceMember(
                        workspaceId = workspaceId,
                        userId = targetUserId,
                        role = role,
                        joinedAt = clock.now(),
                    )
                ).bind()
            }
        }
}

