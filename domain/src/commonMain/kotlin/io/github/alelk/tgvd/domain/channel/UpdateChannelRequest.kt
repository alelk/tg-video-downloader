package io.github.alelk.tgvd.domain.channel

import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.TrackPreferences

/**
 * Patch-style request: only non-null fields overwrite the existing channel.
 * [trackPreferences] replaces the channel's overrides as a whole; an empty value clears them.
 */
data class UpdateChannelRequest(
    val name: String? = null,
    val tags: Set<Tag>? = null,
    val metadataOverrides: MetadataTemplate? = null,
    val notes: String? = null,
    val trackPreferences: TrackPreferences? = null,
)

