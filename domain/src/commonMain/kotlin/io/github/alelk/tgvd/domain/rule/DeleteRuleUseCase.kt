package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/** Deletes a rule of the workspace [workspaceSlug]; a rule of another workspace is [DomainError.RuleNotFound]. */
class DeleteRuleUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        ruleId: RuleId,
    ): Either<DomainError, Unit> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            ruleRepository.findInWorkspace(ruleId, workspace.id).bind()
            if (!ruleRepository.delete(ruleId)) raise(DomainError.RuleNotFound(ruleId))
        }
    }
}
