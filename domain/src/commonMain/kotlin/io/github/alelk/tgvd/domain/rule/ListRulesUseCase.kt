package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/** Lists the rules of the workspace [workspaceSlug], highest priority first. */
class ListRulesUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(workspaceSlug: WorkspaceSlug, actor: TelegramUserId): Either<DomainError, List<Rule>> =
        txRunner.inRoTransaction {
            either {
                val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
                ruleRepository.findByWorkspace(workspace.id)
            }
        }
}
