package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.Category

/**
 * Default output rules for each category.
 * Used when no rule is matched or as starting templates.
 */
object OutputDefaults {

    val MUSIC_VIDEO: List<OutputRule> = listOf(
        OutputRule(
            pathTemplate = "~/Downloads/Media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
            format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
        ),
        OutputRule(
            pathTemplate = "~/Downloads/Media/Music Videos/converted/{artist}/{title}.mp4",
            format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
            embedThumbnail = true,
            embedMetadata = true,
        ),
    )

    val SERIES: List<OutputRule> = listOf(
        OutputRule(
            pathTemplate = "~/Downloads/Media/TV Series/{seriesName}/Season {season}/{episode} - {title}.{ext}",
            format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
            embedThumbnail = true,
            embedMetadata = true,
            embedSubtitles = true,
        ),
    )

    val YT_SERIES: List<OutputRule> = listOf(
        OutputRule(
            pathTemplate = "~/Downloads/Media/Yt Series/{channelName}/{year}/{date} {title}.{ext}",
            format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
            embedThumbnail = true,
            embedMetadata = true,
        ),
    )

    val OTHER: List<OutputRule> = listOf(
        OutputRule(
            pathTemplate = "~/Downloads/Media/Videos/{channelName}/{title} [{videoId}].{ext}",
            format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
        ),
    )

    fun defaultFor(category: Category): List<OutputRule> = when (category) {
        Category.MUSIC_VIDEO -> MUSIC_VIDEO
        Category.SERIES -> SERIES
        Category.OTHER -> OTHER
    }
}
