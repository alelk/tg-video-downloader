package io.github.alelk.tgvd.server.infra.db.table

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object JobOutputsTable : UuidTable("job_outputs") {
    val jobId = reference("job_id", JobsTable)
    val format = varchar("format", 32)
    val path = text("path")
    val size = long("size").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
