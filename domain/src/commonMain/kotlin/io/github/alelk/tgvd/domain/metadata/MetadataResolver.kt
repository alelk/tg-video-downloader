package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.video.VideoInfo

class MetadataResolver {

    fun resolve(video: VideoInfo, template: MetadataTemplate): ResolvedMetadata =
        when (template) {
            is MetadataTemplate.MusicVideo -> resolveMusicVideo(video, template)
            is MetadataTemplate.SeriesEpisode -> resolveSeriesEpisode(video, template)
            is MetadataTemplate.Other -> resolveOther(video, template)
        }

    private fun resolveMusicVideo(
        video: VideoInfo, template: MetadataTemplate.MusicVideo,
    ): ResolvedMetadata.MusicVideo {
        val (fallbackArtist, fallbackTitle) = parseArtistTitle(video.title)

        val artist = template.artistOverride
            ?: template.artistPattern?.let { extractByPattern(video.title, it) }
            ?: fallbackArtist

        val title = template.titleOverride
            ?: template.titlePattern?.let { extractByPattern(video.title, it) }
            ?: fallbackTitle

        return ResolvedMetadata.MusicVideo(
            artist = artist,
            title = title,
            releaseDate = video.uploadDate,
            tags = template.defaultTags,
        )
    }

    private fun resolveSeriesEpisode(
        video: VideoInfo, template: MetadataTemplate.SeriesEpisode,
    ): ResolvedMetadata.SeriesEpisode {
        val seriesName = template.seriesNameOverride ?: video.channelName
        val season = template.seasonPattern?.let { extractByPattern(video.title, it) }
        val episode = template.episodePattern?.let { extractByPattern(video.title, it) }

        return ResolvedMetadata.SeriesEpisode(
            seriesName = seriesName,
            season = season,
            episode = episode,
            title = template.titleOverride ?: video.title,
            releaseDate = video.uploadDate,
            tags = template.defaultTags,
        )
    }

    private fun resolveOther(
        video: VideoInfo, template: MetadataTemplate.Other,
    ): ResolvedMetadata.Other =
        ResolvedMetadata.Other(
            title = template.titleOverride ?: video.title,
            releaseDate = video.uploadDate,
            tags = template.defaultTags,
        )

    private fun extractByPattern(input: String, pattern: String): String? =
        runCatching { pattern.toRegex().find(input)?.groupValues?.getOrNull(1)?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun parseArtistTitle(title: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " — ", ": ")
        for (sep in separators) {
            if (sep in title) {
                val parts = title.split(sep, limit = 2)
                return parts[0].trim() to parts[1].trim()
            }
        }
        return "Unknown Artist" to title
    }
}
