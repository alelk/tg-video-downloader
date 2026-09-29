package io.github.alelk.tgvd.domain.fakes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleRepository

/**
 * In-memory [RuleRepository]; lists are ordered by priority, highest first, like the PostgreSQL adapter.
 *
 * @param refuseSaveWith when set, [save] returns this error instead of storing the rule — for testing
 *   how callers handle a refused rule.
 */
class FakeRuleRepository(var refuseSaveWith: DomainError? = null) : RuleRepository {
    private val rules = linkedMapOf<RuleId, Rule>()

    /** All stored rules, in insertion order. */
    val all: List<Rule> get() = rules.values.toList()

    /** Stores [rule] as is and returns it. */
    fun seed(rule: Rule): Rule = rule.also { rules[it.id] = it }

    override suspend fun findById(id: RuleId): Rule? = rules[id]

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Rule> =
        rules.values.filter { it.workspaceId == workspaceId }.sortedByDescending { it.priority }

    override suspend fun findAllEnabled(): List<Rule> =
        rules.values.filter { it.enabled }.sortedByDescending { it.priority }

    override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId): List<Rule> =
        findByWorkspace(workspaceId).filter { it.enabled }

    override suspend fun save(rule: Rule): Either<DomainError, Rule> {
        refuseSaveWith?.let { return it.left() }
        rules[rule.id] = rule
        return rule.right()
    }

    override suspend fun delete(id: RuleId): Boolean = rules.remove(id) != null
}
