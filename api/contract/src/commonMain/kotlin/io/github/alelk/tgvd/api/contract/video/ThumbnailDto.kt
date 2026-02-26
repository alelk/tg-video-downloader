package io.github.alelk.tgvd.api.contract.video

import kotlinx.serialization.Serializable

@Serializable
data class ThumbnailDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

