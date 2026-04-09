package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.tx.TransactionRunner

class DeleteRuleUseCase(
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(ruleId: RuleId): Either<DomainError, Unit> =
        txRunner.inRwTransaction {
            either {
                if (!ruleRepository.delete(ruleId)) raise(DomainError.RuleNotFound(ruleId))
            }
        }
}

