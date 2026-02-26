package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.Category

data class OutputTemplate(
    val pathTemplate: String,
    val format: OutputFormat,
)

data class StoragePolicy(
    val originalTemplate: String,
    val additionalOutputs: List<OutputTemplate> = emptyList(),
) {
    init {
        require(originalTemplate.isNotBlank()) { "Original template must not be blank" }
    }

    companion object {
        val MUSIC_VIDEO_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
            additionalOutputs = listOf(
                OutputTemplate(
                    pathTemplate = "/media/Music Videos/converted/{artist}/{title}.mp4",
                    format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
                ),
            ),
        )

        val SERIES_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/TV Series/{seriesName}/Season {season}/{episode} - {title}.{ext}",
        )

        val YT_SERIES_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/Yt Series/{channelName}/{year}/{date} {title}.{ext}"
        )

        val OTHER_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/Videos/{channelName}/{title} [{videoId}].{ext}",
        )

        fun defaultFor(category: Category): StoragePolicy = when (category) {
            Category.MUSIC_VIDEO -> MUSIC_VIDEO_DEFAULT
            Category.SERIES -> SERIES_DEFAULT
            Category.OTHER -> OTHER_DEFAULT
        }
    }
}
