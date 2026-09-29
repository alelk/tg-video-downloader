package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner

/** The members of the workspace [workspaceSlug]; only a member may list them. */
class ListWorkspaceMembersUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
    ): Either<DomainError, List<WorkspaceMember>> = txRunner.inRoTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            workspaceRepository.findMembers(workspace.id)
        }
    }
}
