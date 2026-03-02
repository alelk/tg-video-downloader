package io.github.alelk.tgvd.api.contract.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Content category. Mirrors domain `Category`. */
@Serializable
enum class CategoryDto {
    @SerialName("music-video") MUSIC_VIDEO,
    @SerialName("series-episode") SERIES_EPISODE,
    @SerialName("other") OTHER,
}
