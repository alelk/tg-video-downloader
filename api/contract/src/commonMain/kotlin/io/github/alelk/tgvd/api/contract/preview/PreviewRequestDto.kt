package io.github.alelk.tgvd.api.contract.preview

import kotlinx.serialization.Serializable

@Serializable
data class PreviewRequestDto(
    val url: String,
)

