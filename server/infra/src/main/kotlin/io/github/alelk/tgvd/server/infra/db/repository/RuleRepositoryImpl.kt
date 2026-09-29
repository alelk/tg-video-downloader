package io.github.alelk.tgvd.server.infra.db.repository

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.server.infra.db.catchingDb
import io.github.alelk.tgvd.server.infra.db.mapping.categoryDbString
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.table.RulesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

/**
 * Runs in the transaction of the caller (`TransactionRunner`); never opens one.
 * An insert stores the timestamps of the [Rule]; an update stamps `updated_at` with [clock].
 */
@OptIn(ExperimentalUuidApi::class)
class RuleRepositoryImpl(private val clock: Clock) : RuleRepository {
    override suspend fun findById(id: RuleId): Rule? = RulesTable.selectAll()
        .where { RulesTable.id eq id.value }
        .singleOrNull()
        ?.toRule()

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Rule> = RulesTable.selectAll()
        .where { RulesTable.workspaceId eq workspaceId.value }
        .orderBy(RulesTable.priority, SortOrder.DESC)
        .map { it.toRule() }

    override suspend fun findAllEnabled(): List<Rule> = RulesTable.selectAll()
        .where { RulesTable.enabled eq true }
        .orderBy(RulesTable.priority, SortOrder.DESC)
        .map { it.toRule() }

    override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId): List<Rule> = RulesTable.selectAll()
        .where { (RulesTable.workspaceId eq workspaceId.value) and (RulesTable.enabled eq true) }
        .orderBy(RulesTable.priority, SortOrder.DESC)
        .map { it.toRule() }

    override suspend fun save(rule: Rule): Either<DomainError, Rule> = catchingDb {
        val exists = RulesTable.selectAll()
            .where { RulesTable.id eq rule.id.value }
            .count() > 0

        if (exists) {
            RulesTable.update({ RulesTable.id eq rule.id.value }) {
                it[name] = rule.name
                it[workspaceId] = rule.workspaceId.value
                it[enabled] = rule.enabled
                it[priority] = rule.priority
                it[match] = rule.match.toPm()
                it[category] = rule.metadataTemplate.categoryDbString()
                it[metadataTemplate] = rule.metadataTemplate.toPm()
                it[downloadPolicy] = rule.downloadPolicy.toPm()
                it[outputs] = rule.outputs.map { o -> o.toPm() }
                it[updatedAt] = clock.now()
            }
        } else {
            RulesTable.insert {
                it[id] = rule.id.value
                it[name] = rule.name
                it[workspaceId] = rule.workspaceId.value
                it[enabled] = rule.enabled
                it[priority] = rule.priority
                it[match] = rule.match.toPm()
                it[category] = rule.metadataTemplate.categoryDbString()
                it[metadataTemplate] = rule.metadataTemplate.toPm()
                it[downloadPolicy] = rule.downloadPolicy.toPm()
                it[outputs] = rule.outputs.map { o -> o.toPm() }
                it[createdAt] = rule.createdAt
                it[updatedAt] = rule.updatedAt
            }
        }
        rule.right()
    }

    override suspend fun delete(id: RuleId): Boolean = RulesTable.deleteWhere { RulesTable.id eq id.value } > 0

    private fun ResultRow.toRule(): Rule = Rule(
        id = RuleId(this[RulesTable.id].value),
        name = this[RulesTable.name],
        workspaceId = WorkspaceId(this[RulesTable.workspaceId].value),
        match = this[RulesTable.match].toDomain(),
        metadataTemplate = this[RulesTable.metadataTemplate].toDomain(),
        downloadPolicy = this[RulesTable.downloadPolicy].toDomain(),
        outputs = this[RulesTable.outputs].map { it.toDomain() },
        enabled = this[RulesTable.enabled],
        priority = this[RulesTable.priority],
        createdAt = this[RulesTable.createdAt],
        updatedAt = this[RulesTable.updatedAt],
    )
}
