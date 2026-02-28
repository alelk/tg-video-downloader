package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.server.infra.db.model.ResolvedMetadataPm

internal fun ResolvedMetadata.toPm(): ResolvedMetadataPm = when (this) {
    is ResolvedMetadata.MusicVideo -> ResolvedMetadataPm.MusicVideo(
        artist = artist, title = title, releaseDate = releaseDate?.value, tags = tags, comment = comment,
    )
    is ResolvedMetadata.SeriesEpisode -> ResolvedMetadataPm.SeriesEpisode(
        seriesName = seriesName, season = season, episode = episode,
        title = title, releaseDate = releaseDate?.value, tags = tags, comment = comment,
    )
    is ResolvedMetadata.Other -> ResolvedMetadataPm.Other(
        title = title, releaseDate = releaseDate?.value, tags = tags, comment = comment,
    )
}

internal fun ResolvedMetadataPm.toDomain(): ResolvedMetadata = when (this) {
    is ResolvedMetadataPm.MusicVideo -> ResolvedMetadata.MusicVideo(
        artist = artist, title = title, releaseDate = releaseDate?.let { LocalDate(it) },
        tags = tags, comment = comment,
    )
    is ResolvedMetadataPm.SeriesEpisode -> ResolvedMetadata.SeriesEpisode(
        seriesName = seriesName, season = season, episode = episode,
        title = title, releaseDate = releaseDate?.let { LocalDate(it) },
        tags = tags, comment = comment,
    )
    is ResolvedMetadataPm.Other -> ResolvedMetadata.Other(
        title = title, releaseDate = releaseDate?.let { LocalDate(it) },
        tags = tags, comment = comment,
    )
}

