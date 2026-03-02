package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Output format for saved files.
 *
 * Sealed interface mirroring domain `OutputFormat`. Serializes as a plain string `"kind/extension"`:
 * - `"original/webm"`, `"video/mp4"`, `"audio/m4a"`, `"image/jpg"`
 */
@Serializable(with = OutputFormatDto.Serializer::class)
sealed interface OutputFormatDto {
    val extension: String
    val serialized: String

    data class OriginalVideo(val container: MediaContainerDto) : OutputFormatDto {
        override val extension: String get() = container.extension
        override val serialized: String get() = "original/${container.extension}"
    }

    data class ConvertedVideo(val container: MediaContainerDto) : OutputFormatDto {
        override val extension: String get() = container.extension
        override val serialized: String get() = "video/${container.extension}"
    }

    data class Audio(val format: AudioFormatDto) : OutputFormatDto {
        override val extension: String get() = format.extension
        override val serialized: String get() = "audio/${format.extension}"
    }

    data class Thumbnail(val format: ImageFormatDto = ImageFormatDto.JPG) : OutputFormatDto {
        override val extension: String get() = format.extension
        override val serialized: String get() = "image/${format.extension}"
    }

    companion object {
        fun parse(value: String): OutputFormatDto {
            val (kind, ext) = value.split("/", limit = 2).also {
                require(it.size == 2) { "Invalid OutputFormatDto: '$value', expected 'kind/extension'" }
            }
            return when (kind) {
                "original" -> OriginalVideo(
                    MediaContainerDto.fromExtension(ext) ?: error("Unknown container: $ext")
                )
                "video" -> ConvertedVideo(
                    MediaContainerDto.fromExtension(ext) ?: error("Unknown container: $ext")
                )
                "audio" -> AudioFormatDto.fromExtension(ext)
                    ?.let { Audio(it) }
                    ?: error("Unknown audio format: $ext")
                "image" -> ImageFormatDto.fromExtension(ext)
                    ?.let { Thumbnail(it) }
                    ?: error("Unknown image format: $ext")
                else -> error("Unknown OutputFormat kind: $kind")
            }
        }

        /** All possible format values for UI dropdowns */
        val allFormats: List<OutputFormatDto> = buildList {
            MediaContainerDto.entries.forEach { add(OriginalVideo(it)) }
            MediaContainerDto.entries.forEach { add(ConvertedVideo(it)) }
            AudioFormatDto.entries.forEach { add(Audio(it)) }
            ImageFormatDto.entries.forEach { add(Thumbnail(it)) }
        }
    }

    object Serializer : KSerializer<OutputFormatDto> {
        override val descriptor = PrimitiveSerialDescriptor("OutputFormatDto", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: OutputFormatDto) {
            encoder.encodeString(value.serialized)
        }

        override fun deserialize(decoder: Decoder): OutputFormatDto {
            val value = decoder.decodeString()
            return try {
                parse(value)
            } catch (e: Exception) {
                throw SerializationException("Invalid OutputFormatDto: '$value'", e)
            }
        }
    }
}
