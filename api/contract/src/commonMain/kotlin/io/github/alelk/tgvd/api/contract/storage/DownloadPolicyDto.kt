package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class DownloadPolicyDto(
    val maxQuality: VideoQualityDto = VideoQualityDto.BEST,
    /** null = inherit the global subtitle default; true/false = force on/off for this rule. */
    val downloadSubtitles: Boolean? = null,
    val subtitleLanguages: List<String> = emptyList(),
    val writeThumbnail: Boolean = false,
)
