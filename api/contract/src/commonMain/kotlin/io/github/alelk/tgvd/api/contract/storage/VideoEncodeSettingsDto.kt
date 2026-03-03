package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Настройки кодирования видео при конвертации.
 * Применяются только когда видео реально перекодируется.
 */
@Serializable
data class VideoEncodeSettingsDto(
    val codec: VideoCodecDto = VideoCodecDto.H264,
    val hwAccel: HwAccelDto? = null,
    val preset: EncodePresetDto = EncodePresetDto.MEDIUM,
    val crf: Int = 23,
    val audioBitrate: String = "192k",
    val audioCodec: String? = null,
)

@Serializable
enum class VideoCodecDto {
    @SerialName("h264") H264,
    @SerialName("h265") H265,
    @SerialName("vp9") VP9,
    @SerialName("av1") AV1,
}

@Serializable
enum class HwAccelDto {
    @SerialName("videotoolbox") VIDEOTOOLBOX,
    @SerialName("nvenc") NVENC,
    @SerialName("qsv") QSV,
    @SerialName("vaapi") VAAPI,
    @SerialName("amf") AMF,
}

@Serializable
enum class EncodePresetDto {
    @SerialName("ultrafast") ULTRAFAST,
    @SerialName("superfast") SUPERFAST,
    @SerialName("veryfast") VERYFAST,
    @SerialName("faster") FASTER,
    @SerialName("fast") FAST,
    @SerialName("medium") MEDIUM,
    @SerialName("slow") SLOW,
    @SerialName("slower") SLOWER,
    @SerialName("veryslow") VERYSLOW,
}

