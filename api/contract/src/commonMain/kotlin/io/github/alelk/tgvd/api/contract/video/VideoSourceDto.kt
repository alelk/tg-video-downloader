package io.github.alelk.tgvd.api.contract.video

import kotlinx.serialization.Serializable

@Serializable
data class VideoSourceDto(
    val url: String,
    val videoId: String,
    val extractor: String,
)

