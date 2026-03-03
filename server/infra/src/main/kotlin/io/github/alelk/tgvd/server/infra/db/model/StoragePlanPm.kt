package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class StoragePlanPm(
    val original: OutputTargetPm,
    val additional: List<OutputTargetPm> = emptyList(),
)

@Serializable
data class OutputTargetPm(
    val path: String,
    val format: String,
    val maxQuality: String? = null,
    val encodeSettings: VideoEncodeSettingsPm? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

