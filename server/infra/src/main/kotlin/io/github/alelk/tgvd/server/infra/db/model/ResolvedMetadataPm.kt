package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ResolvedMetadataPm {
    val title: String
    val releaseDate: String?
    val tags: List<String>
    val comment: String?

    @Serializable
    @SerialName("music-video")
    data class MusicVideo(
        val artist: String,
        override val title: String,
        val album: String? = null,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataPm

    @Serializable
    @SerialName("series-episode")
    data class SeriesEpisode(
        val seriesName: String,
        val season: String? = null,
        val episode: String? = null,
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataPm

    @Serializable
    @SerialName("other")
    data class Other(
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataPm
}

