package io.github.alelk.tgvd.domain.preview

import io.github.alelk.tgvd.domain.common.Category

sealed interface UserOverrides {

    data class MusicVideo(
        val artist: String? = null,
        val title: String? = null,
        val album: String? = null,
    ) : UserOverrides

    data class SeriesEpisode(
        val seriesName: String? = null,
        val season: String? = null,
        val episode: String? = null,
        val title: String? = null,
    ) : UserOverrides

    data class Other(
        val title: String? = null,
    ) : UserOverrides
}

val UserOverrides.category: Category
    get() = when (this) {
        is UserOverrides.MusicVideo -> Category.MUSIC_VIDEO
        is UserOverrides.SeriesEpisode -> Category.SERIES
        is UserOverrides.Other -> Category.OTHER
    }

