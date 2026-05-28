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
    val availableFormats: List<VideoFormatDto> = emptyList(),
    val actualFormat: VideoFormatDto? = null,
)

@Serializable
data class VideoFormatDto(
    val formatId: String,
    val extension: String,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val tbr: Double? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val formatNote: String? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
)

