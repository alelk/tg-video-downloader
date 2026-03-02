package io.github.alelk.tgvd.server.infra.db.table

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object WorkspacesTable : UuidTable("workspaces") {
    val slug = text("slug").uniqueIndex()
    val name = text("name")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
