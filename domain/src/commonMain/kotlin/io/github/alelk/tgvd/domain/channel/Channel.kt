package io.github.alelk.tgvd.domain.channel

import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import kotlin.time.Instant

/**
 * A channel directory entry — a channel registered in a workspace with tags and optional metadata overrides.
 *
 * The combination of [channelId] + [extractor] uniquely identifies a channel on a platform.
 * [metadataOverrides] allows per-channel metadata customization (e.g., artist name, series name).
 */
data class Channel(
    val id: ChannelDirectoryEntryId,
    val workspaceId: WorkspaceId,
    val channelId: ChannelId,
    val extractor: Extractor,
    val name: String,
    val tags: Set<Tag>,
    val metadataOverrides: MetadataTemplate? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Channel name cannot be blank" }
    }
}

