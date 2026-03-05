package io.github.alelk.tgvd.api.contract.channel

import kotlinx.serialization.Serializable

@Serializable
data class TagListResponseDto(
    val tags: List<String>,
)

