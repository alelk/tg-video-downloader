package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Image format. Mirrors domain `ImageFormat`. */
@Serializable
enum class ImageFormatDto(val extension: String) {
    @SerialName("jpg") JPG("jpg"),
    @SerialName("png") PNG("png"),
    @SerialName("webp") WEBP("webp");

    companion object {
        fun fromExtension(ext: String): ImageFormatDto? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}

