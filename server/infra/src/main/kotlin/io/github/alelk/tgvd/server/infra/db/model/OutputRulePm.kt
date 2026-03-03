package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class OutputRulePm(
    val pathTemplate: String,
    val format: String,
    val maxQuality: String? = null,
    val encodeSettings: VideoEncodeSettingsPm? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

@Serializable
data class VideoEncodeSettingsPm(
    val codec: String = "h264",
    val hwAccel: String? = null,
    val preset: String = "medium",
    val crf: Int = 23,
    val audioBitrate: String = "192k",
    val audioCodec: String? = null,
)

