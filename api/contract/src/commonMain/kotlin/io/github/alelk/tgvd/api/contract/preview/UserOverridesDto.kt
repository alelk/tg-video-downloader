package io.github.alelk.tgvd.api.contract.preview

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface UserOverridesDto {

    @Serializable
    @SerialName("music-video")
    data class MusicVideo(
        val artist: String? = null,
        val title: String? = null,
        val album: String? = null,
    ) : UserOverridesDto

    @Serializable
    @SerialName("series-episode")
    data class SeriesEpisode(
        val seriesName: String? = null,
        val season: String? = null,
        val episode: String? = null,
        val title: String? = null,
    ) : UserOverridesDto

    @Serializable
    @SerialName("other")
    data class Other(
        val title: String? = null,
    ) : UserOverridesDto
}

