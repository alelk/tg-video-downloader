package io.github.alelk.tgvd.domain.storage

sealed interface OutputFormat {
    val extension: String

    data class OriginalVideo(val container: MediaContainer) : OutputFormat {
        override val extension: String get() = container.extension
    }

    data class ConvertedVideo(val container: MediaContainer) : OutputFormat {
        override val extension: String get() = container.extension
    }

    data class Audio(val format: AudioFormat) : OutputFormat {
        override val extension: String get() = format.extension
    }

    data class Thumbnail(val format: ImageFormat = ImageFormat.JPG) : OutputFormat {
        override val extension: String get() = format.extension
    }

    val serialized: String
        get() = when (this) {
            is OriginalVideo -> "original/${container.extension}"
            is ConvertedVideo -> "video/${container.extension}"
            is Audio -> "audio/${format.extension}"
            is Thumbnail -> "image/${format.extension}"
        }

    companion object {
        fun parse(value: String): OutputFormat {
            val (kind, ext) = value.split("/", limit = 2).also {
                require(it.size == 2) { "Invalid OutputFormat: '$value', expected 'kind/extension'" }
            }
            return when (kind) {
                "original" -> OriginalVideo(
                    MediaContainer.fromExtension(ext) ?: error("Unknown container: $ext")
                )
                "video" -> ConvertedVideo(
                    MediaContainer.fromExtension(ext) ?: error("Unknown container: $ext")
                )
                "audio" -> AudioFormat.entries.find { it.extension == ext }
                    ?.let { Audio(it) }
                    ?: error("Unknown audio format: $ext")
                "image" -> ImageFormat.entries.find { it.extension == ext }
                    ?.let { Thumbnail(it) }
                    ?: error("Unknown image format: $ext")
                else -> error("Unknown OutputFormat kind: $kind")
            }
        }
    }
}
