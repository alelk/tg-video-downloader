package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.server.infra.db.table.ChannelsTable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal fun ResultRow.toChannel(): Channel = Channel(
    id = ChannelDirectoryEntryId(this[ChannelsTable.id].value),
    workspaceId = WorkspaceId(this[ChannelsTable.workspaceId].value),
    channelId = ChannelId(this[ChannelsTable.channelId]),
    extractor = Extractor(this[ChannelsTable.extractor]),
    name = this[ChannelsTable.name],
    tags = this[ChannelsTable.tags].map { Tag(it) }.toSet(),
    metadataOverrides = this[ChannelsTable.metadataOverrides]?.toDomain(),
    notes = this[ChannelsTable.notes],
    createdAt = this[ChannelsTable.createdAt],
    updatedAt = this[ChannelsTable.updatedAt],
)

