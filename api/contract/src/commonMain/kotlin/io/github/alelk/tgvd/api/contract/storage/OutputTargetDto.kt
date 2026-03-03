package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class OutputTargetDto(
    val path: String,
    val format: OutputFormatDto,
    val maxQuality: VideoQualityDto? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

