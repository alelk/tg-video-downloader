package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class DownloadPolicyDto(
    val maxQuality: VideoQualityDto = VideoQualityDto.BEST,
    val preferredContainer: MediaContainerDto? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
)
