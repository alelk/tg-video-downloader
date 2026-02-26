package io.github.alelk.tgvd.domain.metadata

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
