package io.github.alelk.tgvd.api.mapping.channel

import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.domain.channel.CreateChannelRequest
import io.github.alelk.tgvd.domain.channel.UpdateChannelRequest
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId

fun CreateChannelDto.toDomain(workspaceId: WorkspaceId): CreateChannelRequest = CreateChannelRequest(
    workspaceId = workspaceId,
    channelId = ChannelId(channelId),
    extractor = Extractor(extractor),
    name = name,
    tags = tags.map { Tag(it) }.toSet(),
    metadataOverrides = metadataOverrides?.toDomain(),
    notes = notes,
)

fun UpdateChannelDto.toDomain(): UpdateChannelRequest = UpdateChannelRequest(
    name = name,
    tags = tags?.map { Tag(it) }?.toSet(),
    metadataOverrides = metadataOverrides?.toDomain(),
    notes = notes,
)

