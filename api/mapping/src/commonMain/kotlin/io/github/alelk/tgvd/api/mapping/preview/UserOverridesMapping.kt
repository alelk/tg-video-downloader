package io.github.alelk.tgvd.api.mapping.preview

import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.domain.preview.UserOverrides

fun UserOverridesDto.toDomain(): UserOverrides = when (this) {
    is UserOverridesDto.MusicVideo -> UserOverrides.MusicVideo(
        artist = artist,
        title = title,
        album = album,
    )
    is UserOverridesDto.SeriesEpisode -> UserOverrides.SeriesEpisode(
        seriesName = seriesName,
        season = season,
        episode = episode,
        title = title,
    )
    is UserOverridesDto.Other -> UserOverrides.Other(
        title = title,
    )
}

fun UserOverrides.toDto(): UserOverridesDto = when (this) {
    is UserOverrides.MusicVideo -> UserOverridesDto.MusicVideo(
        artist = artist,
        title = title,
        album = album,
    )
    is UserOverrides.SeriesEpisode -> UserOverridesDto.SeriesEpisode(
        seriesName = seriesName,
        season = season,
        episode = episode,
        title = title,
    )
    is UserOverrides.Other -> UserOverridesDto.Other(
        title = title,
    )
}

