package io.github.alelk.tgvd.api.contract.video

import kotlinx.serialization.Serializable

@Serializable
data class MediaSelectionDto(
    val audioFormatIds: List<String>? = null,
    val subtitleLanguages: List<String>? = null,
)

@Serializable
data class SubtitleTrackDto(
    val language: String,
    val automatic: Boolean,
    val name: String? = null,
)
