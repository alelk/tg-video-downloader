package io.github.alelk.tgvd.api.mapping.metadata

import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate

fun MetadataTemplate.toDto(): MetadataTemplateDto = when (this) {
    is MetadataTemplate.MusicVideo ->
        MetadataTemplateDto.MusicVideo(artistOverride, artistPattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplate.SeriesEpisode ->
        MetadataTemplateDto.SeriesEpisode(seriesNameOverride, seasonPattern, episodePattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplate.Other ->
        MetadataTemplateDto.Other(titleOverride, titlePattern, defaultTags)
}

fun ResolvedMetadata.toDto(): ResolvedMetadataDto = when (this) {
    is ResolvedMetadata.MusicVideo -> ResolvedMetadataDto.MusicVideo(
        artist = artist, title = title, album = album, releaseDate = releaseDate?.value, tags = tags, comment = comment,
    )
    is ResolvedMetadata.SeriesEpisode -> ResolvedMetadataDto.SeriesEpisode(
        seriesName = seriesName, season = season, episode = episode,
        title = title, releaseDate = releaseDate?.value, tags = tags, comment = comment,
    )
    is ResolvedMetadata.Other -> ResolvedMetadataDto.Other(
        title = title, releaseDate = releaseDate?.value, tags = tags, comment = comment,
    )
}

fun MetadataSource.toDto(): MetadataSourceDto = when (this) {
    MetadataSource.RULE -> MetadataSourceDto.RULE
    MetadataSource.LLM -> MetadataSourceDto.LLM
    MetadataSource.FALLBACK -> MetadataSourceDto.FALLBACK
}

