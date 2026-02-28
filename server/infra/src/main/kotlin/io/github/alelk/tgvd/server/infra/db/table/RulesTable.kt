package io.github.alelk.tgvd.server.infra.db.table

import io.github.alelk.tgvd.server.infra.db.jsonb
import io.github.alelk.tgvd.server.infra.db.model.DownloadPolicyPm
import io.github.alelk.tgvd.server.infra.db.model.MetadataTemplatePm
import io.github.alelk.tgvd.server.infra.db.model.PostProcessPolicyPm
import io.github.alelk.tgvd.server.infra.db.model.RuleMatchPm
import io.github.alelk.tgvd.server.infra.db.model.StoragePolicyPm
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object RulesTable : UuidTable("rules") {
    val workspaceId = reference("workspace_id", WorkspacesTable)
    val name = text("name").default("")
    val enabled = bool("enabled").default(true)
    val priority = integer("priority").default(0)
    val match = jsonb<RuleMatchPm>("match", jsonb)
    val category = varchar("category", 50)
    val metadataTemplate = jsonb<MetadataTemplatePm>("metadata_template", jsonb)
    val downloadPolicy = jsonb<DownloadPolicyPm>("download_policy", jsonb)
    val storagePolicy = jsonb<StoragePolicyPm>("storage_policy", jsonb)
    val postProcessPolicy = jsonb<PostProcessPolicyPm>("post_process_policy", jsonb)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}
