package io.github.alelk.tgvd.domain.channel

import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.TrackPreferences
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A new channel directory entry; the workspace it belongs to is given by the use-case that creates it. */
data class CreateChannelRequest(
    val channelId: ChannelId,
    val extractor: Extractor,
    val name: String,
    val tags: Set<Tag> = emptySet(),
    val metadataOverrides: MetadataTemplate? = null,
    val notes: String? = null,
    val trackPreferences: TrackPreferences? = null,
)

/** Creates a new [Channel] of [workspaceId] from this request, assigning a random id and the given timestamps. */
@OptIn(ExperimentalUuidApi::class)
fun CreateChannelRequest.toChannel(
    workspaceId: WorkspaceId,
    createdAt: Instant,
    updatedAt: Instant = createdAt,
): Channel = Channel(
    id = ChannelDirectoryEntryId(Uuid.random()),
    workspaceId = workspaceId,
    channelId = channelId,
    extractor = extractor,
    name = name,
    tags = tags,
    metadataOverrides = metadataOverrides,
    notes = notes,
    trackPreferences = trackPreferences?.takeUnless { it.isEmpty },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
