package io.github.alelk.tgvd.domain.metadata

import io.github.alelk.tgvd.domain.common.Category

sealed interface MetadataTemplate {
    val titleOverride: String?
    val titlePattern: String?
    val defaultTags: List<String>

    data class MusicVideo(
        val artistOverride: String? = null,
        val artistPattern: String? = null,
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate

    data class SeriesEpisode(
        val seriesNameOverride: String? = null,
        val seasonPattern: String? = null,
        val episodePattern: String? = null,
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate

    data class Other(
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate
}

val MetadataTemplate.category: Category
    get() = when (this) {
        is MetadataTemplate.MusicVideo -> Category.MUSIC_VIDEO
        is MetadataTemplate.SeriesEpisode -> Category.SERIES
        is MetadataTemplate.Other -> Category.OTHER
    }

