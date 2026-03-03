package io.github.alelk.tgvd.domain.storage

/**
 * Настройки кодирования видео при конвертации.
 *
 * Применяются только когда видео реально перекодируется (ConvertedVideo с maxQuality).
 * Если видео просто ремуксится (смена контейнера без изменения потоков) — игнорируются.
 *
 * @param codec видеокодек (H264, H265, VP9, AV1, etc.)
 * @param hwAccel аппаратное ускорение (null = софтварное)
 * @param preset скорость кодирования (влияет на качество и время)
 * @param crf Constant Rate Factor — качество (0 = lossless, 51 = worst; типично 18–28)
 * @param audioBitrate битрейт аудио (e.g. "192k", "128k", "320k")
 * @param audioCodec аудиокодек (null = выбирается автоматически по контейнеру)
 */
data class VideoEncodeSettings(
    val codec: VideoCodec = VideoCodec.H264,
    val hwAccel: HwAccel? = null,
    val preset: EncodePreset = EncodePreset.MEDIUM,
    val crf: Int = 23,
    val audioBitrate: String = "192k",
    val audioCodec: String? = null,
) {
    init {
        require(crf in 0..51) { "CRF must be in range 0..51, got $crf" }
    }

    enum class VideoCodec {
        H264, H265, VP9, AV1;

        /** FFmpeg encoder name for software encoding. */
        val ffmpegEncoder: String
            get() = when (this) {
                H264 -> "libx264"
                H265 -> "libx265"
                VP9 -> "libvpx-vp9"
                AV1 -> "libaom-av1"
            }
    }

    enum class HwAccel {
        VIDEOTOOLBOX,  // macOS
        NVENC,         // NVIDIA
        QSV,           // Intel Quick Sync
        VAAPI,         // Linux VA-API
        AMF,           // AMD (Windows)
        ;

        /** FFmpeg encoder for this HW + given codec. Null if combo not supported. */
        fun encoderFor(codec: VideoCodec): String? = when (this) {
            VIDEOTOOLBOX -> when (codec) {
                VideoCodec.H264 -> "h264_videotoolbox"
                VideoCodec.H265 -> "hevc_videotoolbox"
                else -> null
            }
            NVENC -> when (codec) {
                VideoCodec.H264 -> "h264_nvenc"
                VideoCodec.H265 -> "hevc_nvenc"
                VideoCodec.AV1 -> "av1_nvenc"
                else -> null
            }
            QSV -> when (codec) {
                VideoCodec.H264 -> "h264_qsv"
                VideoCodec.H265 -> "hevc_qsv"
                VideoCodec.AV1 -> "av1_qsv"
                else -> null
            }
            VAAPI -> when (codec) {
                VideoCodec.H264 -> "h264_vaapi"
                VideoCodec.H265 -> "hevc_vaapi"
                VideoCodec.AV1 -> "av1_vaapi"
                else -> null
            }
            AMF -> when (codec) {
                VideoCodec.H264 -> "h264_amf"
                VideoCodec.H265 -> "hevc_amf"
                else -> null
            }
        }
    }

    enum class EncodePreset {
        ULTRAFAST, SUPERFAST, VERYFAST, FASTER, FAST, MEDIUM, SLOW, SLOWER, VERYSLOW;

        val ffmpegValue: String get() = name.lowercase()
    }

    /**
     * Resolve ffmpeg encoder name: HW encoder if available, else software fallback.
     */
    fun resolveEncoder(): String =
        hwAccel?.encoderFor(codec) ?: codec.ffmpegEncoder

    /**
     * Resolve audio codec: explicit override or default for container.
     */
    fun resolveAudioCodec(container: MediaContainer): String =
        audioCodec ?: when (container) {
            MediaContainer.MP4, MediaContainer.MOV -> "aac"
            MediaContainer.MKV -> "copy"
            MediaContainer.WEBM -> "libopus"
            MediaContainer.AVI -> "aac"
        }

    companion object {
        /** YouTube-like quality: H264, CRF 23, medium preset. */
        val YOUTUBE_LIKE = VideoEncodeSettings(
            codec = VideoCodec.H264,
            preset = EncodePreset.MEDIUM,
            crf = 23,
            audioBitrate = "128k",
        )

        /** High quality: H264, CRF 18, slow preset. */
        val HIGH_QUALITY = VideoEncodeSettings(
            codec = VideoCodec.H264,
            preset = EncodePreset.SLOW,
            crf = 18,
            audioBitrate = "192k",
        )
    }
}

