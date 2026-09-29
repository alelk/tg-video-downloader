package io.github.alelk.tgvd.domain.workspace

import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.tx.TransactionRunner

/** A workspace together with the caller's membership in it (the caller's role). */
data class WorkspaceMembership(val workspace: Workspace, val membership: WorkspaceMember)

/** The workspaces [actor] is a member of, each with the actor's membership. */
class ListWorkspacesUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(actor: TelegramUserId): List<WorkspaceMembership> = txRunner.inRoTransaction {
        workspaceRepository.findByUser(actor).mapNotNull { membership ->
            workspaceRepository.findById(membership.workspaceId)?.let { WorkspaceMembership(it, membership) }
        }
    }
}
