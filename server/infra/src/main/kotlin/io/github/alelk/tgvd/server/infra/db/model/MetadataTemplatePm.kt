package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MetadataTemplatePm {
    val titleOverride: String?
    val titlePattern: String?
    val defaultTags: List<String>

    @Serializable
    @SerialName("music-video")
    data class MusicVideo(
        val artistOverride: String? = null,
        val artistPattern: String? = null,
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplatePm

    @Serializable
    @SerialName("series-episode")
    data class SeriesEpisode(
        val seriesNameOverride: String? = null,
        val seasonPattern: String? = null,
        val episodePattern: String? = null,
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplatePm

    @Serializable
    @SerialName("other")
    data class Other(
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplatePm
}

