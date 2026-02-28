package io.github.alelk.tgvd.server.infra.db.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object WorkspaceMembersTable : Table("workspace_members") {
    val workspaceId = reference("workspace_id", WorkspacesTable)
    val userId = long("user_id")
    val role = varchar("role", 20).default("member")
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(workspaceId, userId)
}
