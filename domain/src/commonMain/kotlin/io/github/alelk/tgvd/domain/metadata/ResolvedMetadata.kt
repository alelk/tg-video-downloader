package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.common.LocalDate

sealed interface ResolvedMetadata {
    val title: String
    val releaseDate: LocalDate?
    val tags: List<String>
    val comment: String?

    val year: Int? get() = releaseDate?.year

    data class MusicVideo(
        val artist: String,
        override val title: String,
        val album: String? = null,
        override val releaseDate: LocalDate? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(artist.isNotBlank()) { "Artist cannot be blank" }
            require(title.isNotBlank()) { "Title cannot be blank" }
        }
    }

    data class SeriesEpisode(
        val seriesName: String,
        val season: String? = null,
        val episode: String? = null,
        override val title: String,
        override val releaseDate: LocalDate? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(seriesName.isNotBlank()) { "SeriesName cannot be blank" }
            require(title.isNotBlank()) { "Title cannot be blank" }
        }
    }

    data class Other(
        override val title: String,
        override val releaseDate: LocalDate? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(title.isNotBlank()) { "Title cannot be blank" }
        }
    }
}

val ResolvedMetadata.category: Category
    get() = when (this) {
        is ResolvedMetadata.MusicVideo -> Category.MUSIC_VIDEO
        is ResolvedMetadata.SeriesEpisode -> Category.SERIES
        is ResolvedMetadata.Other -> Category.OTHER
    }
