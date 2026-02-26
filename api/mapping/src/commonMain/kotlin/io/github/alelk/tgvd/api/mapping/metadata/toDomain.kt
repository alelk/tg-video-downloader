package io.github.alelk.tgvd.api.mapping.metadata

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata

fun MetadataTemplateDto.toDomain(): MetadataTemplate = when (this) {
    is MetadataTemplateDto.MusicVideo ->
        MetadataTemplate.MusicVideo(artistOverride, artistPattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplateDto.SeriesEpisode ->
        MetadataTemplate.SeriesEpisode(seriesNameOverride, seasonPattern, episodePattern, titleOverride, titlePattern, defaultTags)
    is MetadataTemplateDto.Other ->
        MetadataTemplate.Other(titleOverride, titlePattern, defaultTags)
}


fun ResolvedMetadataDto.toDomain(): Either<DomainError.ValidationError, ResolvedMetadata> = when (this) {
    is ResolvedMetadataDto.MusicVideo -> either {
        ensure(artist.isNotBlank()) { DomainError.ValidationError("artist", "Cannot be blank") }
        ensure(title.isNotBlank()) { DomainError.ValidationError("title", "Cannot be blank") }
        ResolvedMetadata.MusicVideo(
            artist = artist, title = title, releaseDate = releaseDate?.let { LocalDate(it) },
            tags = tags, comment = comment,
        )
    }
    is ResolvedMetadataDto.SeriesEpisode -> either {
        ensure(seriesName.isNotBlank()) { DomainError.ValidationError("seriesName", "Cannot be blank") }
        ensure(title.isNotBlank()) { DomainError.ValidationError("title", "Cannot be blank") }
        ResolvedMetadata.SeriesEpisode(
            seriesName = seriesName, season = season, episode = episode,
            title = title, releaseDate = releaseDate?.let { LocalDate(it) },
            tags = tags, comment = comment,
        )
    }
    is ResolvedMetadataDto.Other -> either {
        ensure(title.isNotBlank()) { DomainError.ValidationError("title", "Cannot be blank") }
        ResolvedMetadata.Other(
            title = title, releaseDate = releaseDate?.let { LocalDate(it) },
            tags = tags, comment = comment,
        )
    }
}

fun MetadataSourceDto.toDomain(): MetadataSource = when (this) {
    MetadataSourceDto.RULE -> MetadataSource.RULE
    MetadataSourceDto.LLM -> MetadataSource.LLM
    MetadataSourceDto.FALLBACK -> MetadataSource.FALLBACK
}

