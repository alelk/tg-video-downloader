package io.github.alelk.tgvd.api.contract.channel

import kotlinx.serialization.Serializable

@Serializable
data class ChannelListResponseDto(
    val items: List<ChannelDto>,
)

