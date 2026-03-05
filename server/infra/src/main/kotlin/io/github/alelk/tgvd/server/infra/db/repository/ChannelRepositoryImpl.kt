package io.github.alelk.tgvd.server.infra.db.repository

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.mapping.now
import io.github.alelk.tgvd.server.infra.db.mapping.toChannel
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.table.ChannelsTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

/** PostgreSQL: column @> ARRAY[values]::text[] */
private fun Column<List<String>>.pgArrayContains(values: List<String>): Op<Boolean> =
    object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder {
                append(this@pgArrayContains)
                append(" @> ARRAY[")
                values.forEachIndexed { i, v ->
                    if (i > 0) append(",")
                    append(stringParam(v))
                }
                append("]::text[]")
            }
        }
    }

/** PostgreSQL: column && ARRAY[values]::text[] */
private fun Column<List<String>>.pgArrayOverlaps(values: List<String>): Op<Boolean> =
    object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder {
                append(this@pgArrayOverlaps)
                append(" && ARRAY[")
                values.forEachIndexed { i, v ->
                    if (i > 0) append(",")
                    append(stringParam(v))
                }
                append("]::text[]")
            }
        }
    }

@OptIn(ExperimentalUuidApi::class)
class ChannelRepositoryImpl(
    private val database: Database,
) : ChannelRepository {

    override suspend fun findById(id: ChannelDirectoryEntryId): Channel? = dbQuery(database) {
        ChannelsTable.selectAll()
            .where { ChannelsTable.id eq id.value }
            .singleOrNull()
            ?.toChannel()
    }

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Channel> = dbQuery(database) {
        ChannelsTable.selectAll()
            .where { ChannelsTable.workspaceId eq workspaceId.value }
            .orderBy(ChannelsTable.name, SortOrder.ASC)
            .map { it.toChannel() }
    }

    override suspend fun findByChannelId(
        workspaceId: WorkspaceId,
        channelId: ChannelId,
        extractor: Extractor,
    ): Channel? = dbQuery(database) {
        ChannelsTable.selectAll()
            .where {
                (ChannelsTable.workspaceId eq workspaceId.value) and
                    (ChannelsTable.channelId eq channelId.value) and
                    (ChannelsTable.extractor eq extractor.value)
            }
            .singleOrNull()
            ?.toChannel()
    }

    override suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag): List<Channel> = dbQuery(database) {
        ChannelsTable.selectAll()
            .where {
                (ChannelsTable.workspaceId eq workspaceId.value) and
                    ChannelsTable.tags.pgArrayContains(listOf(tag.value))
            }
            .map { it.toChannel() }
    }

    override suspend fun findByTags(
        workspaceId: WorkspaceId,
        tags: Set<Tag>,
        matchAll: Boolean,
    ): List<Channel> = dbQuery(database) {
        if (tags.isEmpty()) return@dbQuery emptyList()
        val tagValues = tags.map { it.value }
        ChannelsTable.selectAll()
            .where {
                (ChannelsTable.workspaceId eq workspaceId.value) and
                    if (matchAll) ChannelsTable.tags.pgArrayContains(tagValues)
                    else ChannelsTable.tags.pgArrayOverlaps(tagValues)
            }
            .map { it.toChannel() }
    }

    override suspend fun save(channel: Channel): Either<DomainError, Channel> = dbQuery(database) {
        val exists = ChannelsTable.selectAll()
            .where { ChannelsTable.id eq channel.id.value }
            .count() > 0

        if (exists) {
            ChannelsTable.update({ ChannelsTable.id eq channel.id.value }) {
                it[name] = channel.name
                it[workspaceId] = channel.workspaceId.value
                it[channelId] = channel.channelId.value
                it[extractor] = channel.extractor.value
                it[tags] = channel.tags.map { t -> t.value }
                it[metadataOverrides] = channel.metadataOverrides?.toPm()
                it[notes] = channel.notes
                it[updatedAt] = now()
            }
        } else {
            ChannelsTable.insert {
                it[id] = channel.id.value
                it[name] = channel.name
                it[workspaceId] = channel.workspaceId.value
                it[channelId] = channel.channelId.value
                it[extractor] = channel.extractor.value
                it[tags] = channel.tags.map { t -> t.value }
                it[metadataOverrides] = channel.metadataOverrides?.toPm()
                it[notes] = channel.notes
            }
        }
        channel.right()
    }

    override suspend fun delete(id: ChannelDirectoryEntryId): Boolean = dbQuery(database) {
        ChannelsTable.deleteWhere { ChannelsTable.id eq id.value } > 0
    }

    override suspend fun findAllTags(workspaceId: WorkspaceId): Set<Tag> = dbQuery(database) {
        // SELECT DISTINCT unnest(tags) FROM channels WHERE workspace_id = ?
        ChannelsTable.selectAll()
            .where { ChannelsTable.workspaceId eq workspaceId.value }
            .flatMap { it[ChannelsTable.tags] }
            .toSet()
            .map { Tag(it) }
            .toSet()
    }
}





