package io.github.alelk.tgvd.features.download.model

import io.github.alelk.tgvd.api.contract.video.SubtitleTrackDto
import io.github.alelk.tgvd.api.contract.video.VideoFormatDto

fun maxAvailableQualityLabel(formats: List<VideoFormatDto>): String? =
    formats.mapNotNull(VideoFormatDto::height).maxOrNull()?.let(::qualityLabelForHeight)

fun selectAudioOptions(
    formats: List<VideoFormatDto>,
    defaultAudioFormatIds: Collection<String>,
): List<VideoFormatDto> = formats
    .filter(VideoFormatDto::isAudioOnly)
    .groupBy { it.language ?: it.formatId }
    .values
    .mapNotNull { variants ->
        variants.firstOrNull { it.formatId in defaultAudioFormatIds }
            ?: variants.maxByOrNull { it.tbr ?: 0.0 }
    }

fun groupSubtitleOptions(tracks: List<SubtitleTrackDto>): Map<String, List<SubtitleTrackDto>> =
    tracks.groupBy(SubtitleTrackDto::language)

private fun VideoFormatDto.isAudioOnly(): Boolean =
    acodec != null && acodec != "none" && (vcodec == null || vcodec == "none")

private fun qualityLabelForHeight(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "1440p"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    height >= 480 -> "480p"
    height >= 360 -> "360p"
    else -> "${height}p"
}
