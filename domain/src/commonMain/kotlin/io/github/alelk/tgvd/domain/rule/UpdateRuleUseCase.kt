package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class UpdateRuleUseCase(
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(ruleId: RuleId, request: UpdateRuleRequest): Either<DomainError, Rule> =
        txRunner.inRwTransaction {
            either {
                val existing = ruleRepository.findById(ruleId) ?: raise(DomainError.RuleNotFound(ruleId))
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

