package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Media container format. Mirrors domain `MediaContainer`. */
@Serializable
enum class MediaContainerDto(val extension: String) {
    @SerialName("mp4") MP4("mp4"),
    @SerialName("mkv") MKV("mkv"),
    @SerialName("webm") WEBM("webm"),
    @SerialName("avi") AVI("avi"),
    @SerialName("mov") MOV("mov");

    companion object {
        fun fromExtension(ext: String): MediaContainerDto? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}
