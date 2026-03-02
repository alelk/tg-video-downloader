package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Audio format. Mirrors domain `AudioFormat`. */
@Serializable
enum class AudioFormatDto(val extension: String) {
    @SerialName("m4a") M4A("m4a"),
    @SerialName("mp3") MP3("mp3"),
    @SerialName("opus") OPUS("opus"),
    @SerialName("flac") FLAC("flac"),
    @SerialName("wav") WAV("wav");

    companion object {
        fun fromExtension(ext: String): AudioFormatDto? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}

