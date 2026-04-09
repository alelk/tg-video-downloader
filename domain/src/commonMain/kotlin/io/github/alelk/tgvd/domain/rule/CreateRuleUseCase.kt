package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class CreateRuleUseCase(
    private val ruleRepository: RuleRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(request: CreateRuleRequest): Either<DomainError, Rule> =
        txRunner.inRwTransaction {
            either {
                ruleRepository.save(request.toRule(clock.now())).bind()
            }
        }
}

