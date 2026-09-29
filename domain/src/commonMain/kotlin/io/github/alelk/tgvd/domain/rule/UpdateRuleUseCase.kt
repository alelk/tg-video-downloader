package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import kotlin.time.Clock

/**
 * Applies [UpdateRuleRequest] to a rule of the workspace [workspaceSlug].
 * A rule of another workspace is [DomainError.RuleNotFound].
 */
class UpdateRuleUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        ruleId: RuleId,
        request: UpdateRuleRequest,
    ): Either<DomainError, Rule> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            val existing = ruleRepository.findInWorkspace(ruleId, workspace.id).bind()
            val updated =
                existing.copy(
                    name = request.name ?: existing.name,
                    match = request.match ?: existing.match,
                    metadataTemplate = request.metadataTemplate ?: existing.metadataTemplate,
                    downloadPolicy = request.downloadPolicy ?: existing.downloadPolicy,
                    outputs = request.outputs ?: existing.outputs,
                    enabled = request.enabled ?: existing.enabled,
                    priority = request.priority ?: existing.priority,
                    updatedAt = clock.now(),
                )
            ruleRepository.save(updated).bind()
        }
    }
}
