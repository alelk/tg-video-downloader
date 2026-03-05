package io.github.alelk.tgvd.api.mapping.channel

import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.domain.channel.Channel
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Channel.toDto(): ChannelDto = ChannelDto(
    id = id.value.toString(),
    workspaceId = workspaceId.value.toString(),
    channelId = channelId.value,
    extractor = extractor.value,
    name = name,
    tags = tags.map { it.value }.sorted(),
    metadataOverrides = metadataOverrides?.toDto(),
    notes = notes,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

