package io.github.alelk.tgvd.api.contract.metadata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ResolvedMetadataDto {
    val title: String
    val releaseDate: String?
    val tags: List<String>
    val comment: String?

    @Serializable
    @SerialName("music-video")
    data class MusicVideo(
        val artist: String,
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto

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
    ) : ResolvedMetadataDto

    @Serializable
    @SerialName("other")
    data class Other(
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
}


