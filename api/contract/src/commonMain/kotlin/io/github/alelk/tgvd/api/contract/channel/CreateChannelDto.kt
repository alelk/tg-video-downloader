package io.github.alelk.tgvd.api.contract.channel

import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import kotlinx.serialization.Serializable

@Serializable
data class CreateChannelDto(
    val channelId: String,
    val extractor: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val metadataOverrides: MetadataTemplateDto? = null,
    val notes: String? = null,
)

