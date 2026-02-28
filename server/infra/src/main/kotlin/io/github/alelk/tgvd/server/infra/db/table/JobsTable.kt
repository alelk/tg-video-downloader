package io.github.alelk.tgvd.server.infra.db.table

import io.github.alelk.tgvd.server.infra.db.jsonb
import io.github.alelk.tgvd.server.infra.db.model.JobErrorPm
import io.github.alelk.tgvd.server.infra.db.model.JobProgressPm
import io.github.alelk.tgvd.server.infra.db.model.ResolvedMetadataPm
import io.github.alelk.tgvd.server.infra.db.model.StoragePlanPm
import io.github.alelk.tgvd.server.infra.db.model.VideoInfoPm
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object JobsTable : UuidTable("jobs") {
    val workspaceId = reference("workspace_id", WorkspacesTable)
    val status = varchar("status", 20).default("queued")
    val videoId = varchar("video_id", 64)
    val sourceUrl = text("source_url")
    val sourceExtractor = varchar("source_extractor", 50)
    val ruleId = reference("rule_id", RulesTable).nullable()
    val category = varchar("category", 50)
    val rawInfo = jsonb<VideoInfoPm>("raw_info", jsonb)
    val metadata = jsonb<ResolvedMetadataPm>("metadata", jsonb)
    val storagePlan = jsonb<StoragePlanPm>("storage_plan", jsonb)
    val progress = jsonb<JobProgressPm>("progress", jsonb).nullable()
    val error = jsonb<JobErrorPm>("error", jsonb).nullable()
    val attempt = integer("attempt").default(0)
    val createdByTelegramUserId = long("created_by_telegram_user_id")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
}
