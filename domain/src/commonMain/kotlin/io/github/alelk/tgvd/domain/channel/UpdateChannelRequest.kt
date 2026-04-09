package io.github.alelk.tgvd.domain.channel

import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate

/**
 * Patch-style request: only non-null fields overwrite the existing channel.
 */
data class UpdateChannelRequest(
    val name: String? = null,
    val tags: Set<Tag>? = null,
    val metadataOverrides: MetadataTemplate? = null,
    val notes: String? = null,
)

