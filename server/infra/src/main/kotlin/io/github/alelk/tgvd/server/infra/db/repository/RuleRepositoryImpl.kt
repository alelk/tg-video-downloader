package io.github.alelk.tgvd.server.infra.db.repository

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.mapping.categoryDbString
import io.github.alelk.tgvd.server.infra.db.mapping.now
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.table.RulesTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
class RuleRepositoryImpl(
    private val database: Database,
) : RuleRepository {

    override suspend fun findById(id: RuleId): Rule? = dbQuery(database) {
        RulesTable.selectAll()
            .where { RulesTable.id eq id.value }
            .singleOrNull()
            ?.toRule()
    }

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Rule> = dbQuery(database) {
        RulesTable.selectAll()
            .where { RulesTable.workspaceId eq workspaceId.value }
            .orderBy(RulesTable.priority, SortOrder.DESC)
            .map { it.toRule() }
    }

    override suspend fun findAllEnabled(): List<Rule> = dbQuery(database) {
        RulesTable.selectAll()
            .where { RulesTable.enabled eq true }
            .orderBy(RulesTable.priority, SortOrder.DESC)
            .map { it.toRule() }
    }

    override suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId): List<Rule> = dbQuery(database) {
        RulesTable.selectAll()
            .where { (RulesTable.workspaceId eq workspaceId.value) and (RulesTable.enabled eq true) }
            .orderBy(RulesTable.priority, SortOrder.DESC)
            .map { it.toRule() }
    }

    override suspend fun save(rule: Rule): Either<DomainError, Rule> = dbQuery(database) {
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
                it[storagePolicy] = rule.storagePolicy.toPm()
                it[postProcessPolicy] = rule.postProcessPolicy.toPm()
                it[updatedAt] = now()
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
                it[storagePolicy] = rule.storagePolicy.toPm()
                it[postProcessPolicy] = rule.postProcessPolicy.toPm()
            }
        }
        rule.right()
    }

    override suspend fun delete(id: RuleId): Boolean = dbQuery(database) {
        RulesTable.deleteWhere { RulesTable.id eq id.value } > 0
    }

    private fun ResultRow.toRule(): Rule = Rule(
        id = RuleId(this[RulesTable.id].value),
        name = this[RulesTable.name],
        workspaceId = WorkspaceId(this[RulesTable.workspaceId].value),
        match = this[RulesTable.match].toDomain(),
        metadataTemplate = this[RulesTable.metadataTemplate].toDomain(),
        storagePolicy = this[RulesTable.storagePolicy].toDomain(),
        downloadPolicy = this[RulesTable.downloadPolicy].toDomain(),
        postProcessPolicy = this[RulesTable.postProcessPolicy].toDomain(),
        enabled = this[RulesTable.enabled],
        priority = this[RulesTable.priority],
        createdAt = this[RulesTable.createdAt],
        updatedAt = this[RulesTable.updatedAt],
    )
}
