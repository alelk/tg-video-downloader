package io.github.alelk.tgvd.server.infra.db.table

import io.github.alelk.tgvd.server.infra.db.jsonb
import io.github.alelk.tgvd.server.infra.db.model.MetadataTemplatePm
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object ChannelsTable : UuidTable("channels") {
    val workspaceId = reference("workspace_id", WorkspacesTable)
    val channelId = text("channel_id")
    val extractor = text("extractor")
    val name = text("name")
    val tags = array<String>("tags")
    val metadataOverrides = jsonb<MetadataTemplatePm>("metadata_overrides", jsonb).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

