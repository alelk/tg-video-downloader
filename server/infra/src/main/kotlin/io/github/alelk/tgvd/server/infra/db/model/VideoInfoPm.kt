package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoInfoPm(
    val videoId: String,
    val extractor: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val uploadDate: String? = null,
    val durationSeconds: Int,
    val webpageUrl: String,
    val thumbnails: List<ThumbnailPm> = emptyList(),
    val description: String? = null,
)

@Serializable
data class ThumbnailPm(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

