package io.github.alelk.tgvd.domain.rule

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId

interface RuleRepository {
    suspend fun findById(id: RuleId): Rule?
    suspend fun findByOwner(ownerId: TelegramUserId): List<Rule>
    suspend fun findAllEnabled(): List<Rule>
    suspend fun save(rule: Rule): Either<DomainError, Rule>
    suspend fun delete(id: RuleId): Boolean
}
