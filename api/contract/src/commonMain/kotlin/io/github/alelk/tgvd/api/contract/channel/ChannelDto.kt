package io.github.alelk.tgvd.api.contract.channel

import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import kotlinx.serialization.Serializable

@Serializable
data class ChannelDto(
    val id: String,
    val workspaceId: String,
    val channelId: String,
    val extractor: String,
    val name: String,
    val tags: List<String>,
    val metadataOverrides: MetadataTemplateDto? = null,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

