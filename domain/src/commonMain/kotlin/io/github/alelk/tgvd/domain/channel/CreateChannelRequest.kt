package io.github.alelk.tgvd.domain.channel

import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class CreateChannelRequest(
    val workspaceId: WorkspaceId,
    val channelId: ChannelId,
    val extractor: Extractor,
    val name: String,
    val tags: Set<Tag> = emptySet(),
    val metadataOverrides: MetadataTemplate? = null,
    val notes: String? = null,
)

/** Creates a new [Channel] from this request, assigning a random id and the given timestamps. */
@OptIn(ExperimentalUuidApi::class)
fun CreateChannelRequest.toChannel(createdAt: Instant, updatedAt: Instant = createdAt): Channel =
    Channel(
        id = ChannelDirectoryEntryId(Uuid.random()),
        workspaceId = workspaceId,
        channelId = channelId,
        extractor = extractor,
        name = name,
        tags = tags,
        metadataOverrides = metadataOverrides,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

