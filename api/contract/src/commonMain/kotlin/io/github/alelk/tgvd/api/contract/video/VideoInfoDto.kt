package io.github.alelk.tgvd.api.contract.video

import kotlinx.serialization.Serializable

@Serializable
data class VideoInfoDto(
    val videoId: String,
    val extractor: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val uploadDate: String? = null,
    val durationSeconds: Int,
    val webpageUrl: String,
    val thumbnails: List<ThumbnailDto> = emptyList(),
    val description: String? = null,
)

