package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import kotlin.time.Clock

/** Creates a rule in the workspace [workspaceSlug]; the caller must be a member. */
class CreateRuleUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        request: CreateRuleRequest,
    ): Either<DomainError, Rule> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            ruleRepository.save(request.toRule(workspace.id, clock.now())).bind()
        }
    }
}
