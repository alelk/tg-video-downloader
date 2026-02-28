package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.server.infra.db.model.MetadataTemplatePm

internal fun MetadataTemplate.toPm(): MetadataTemplatePm = when (this) {
    is MetadataTemplate.MusicVideo ->
        MetadataTemplatePm.MusicVideo(artistOverride, artistPattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplate.SeriesEpisode ->
        MetadataTemplatePm.SeriesEpisode(seriesNameOverride, seasonPattern, episodePattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplate.Other ->
        MetadataTemplatePm.Other(titleOverride, titlePattern, defaultTags)
}

internal fun MetadataTemplatePm.toDomain(): MetadataTemplate = when (this) {
    is MetadataTemplatePm.MusicVideo ->
        MetadataTemplate.MusicVideo(artistOverride, artistPattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplatePm.SeriesEpisode ->
        MetadataTemplate.SeriesEpisode(seriesNameOverride, seasonPattern, episodePattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplatePm.Other ->
        MetadataTemplate.Other(titleOverride, titlePattern, defaultTags)
}

