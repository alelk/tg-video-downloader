package io.github.alelk.tgvd.api.contract.channel

import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.storage.TrackPreferencesDto
import kotlinx.serialization.Serializable

@Serializable
data class UpdateChannelDto(
    val name: String? = null,
    val tags: List<String>? = null,
    val metadataOverrides: MetadataTemplateDto? = null,
    val notes: String? = null,
    /** null = keep current overrides; an all-null value clears them. */
    val trackPreferences: TrackPreferencesDto? = null,
)

