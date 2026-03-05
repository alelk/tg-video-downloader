package io.github.alelk.tgvd.domain.metadata

/**
 * Merges two MetadataTemplates.
 * Fields from [overlay] take priority over [base].
 * If types differ, overlay wins entirely.
 */
fun mergeTemplates(base: MetadataTemplate, overlay: MetadataTemplate?): MetadataTemplate {
    if (overlay == null) return base
    return when (overlay) {
        is MetadataTemplate.MusicVideo -> {
            val b = base as? MetadataTemplate.MusicVideo
            MetadataTemplate.MusicVideo(
                artistOverride = overlay.artistOverride ?: b?.artistOverride,
                artistPattern = overlay.artistPattern ?: b?.artistPattern,
                titleOverride = overlay.titleOverride ?: b?.titleOverride,
                titlePattern = overlay.titlePattern ?: b?.titlePattern,
                defaultTags = overlay.defaultTags.ifEmpty { b?.defaultTags ?: emptyList() },
            )
        }
        is MetadataTemplate.SeriesEpisode -> {
            val b = base as? MetadataTemplate.SeriesEpisode
            MetadataTemplate.SeriesEpisode(
                seriesNameOverride = overlay.seriesNameOverride ?: b?.seriesNameOverride,
                seasonPattern = overlay.seasonPattern ?: b?.seasonPattern,
                episodePattern = overlay.episodePattern ?: b?.episodePattern,
                titleOverride = overlay.titleOverride ?: b?.titleOverride,
                titlePattern = overlay.titlePattern ?: b?.titlePattern,
                defaultTags = overlay.defaultTags.ifEmpty { b?.defaultTags ?: emptyList() },
            )
        }
        is MetadataTemplate.Other -> {
            val b = base as? MetadataTemplate.Other
            MetadataTemplate.Other(
                titleOverride = overlay.titleOverride ?: b?.titleOverride,
                titlePattern = overlay.titlePattern ?: b?.titlePattern,
                defaultTags = overlay.defaultTags.ifEmpty { b?.defaultTags ?: emptyList() },
            )
        }
    }
}

