package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

/**
 * DTO for a single output rule.
 * Each output describes: where to save, in what format, at what quality, and what post-processing to apply.
 */
@Serializable
data class OutputRuleDto(
    val pathTemplate: String,
    val format: OutputFormatDto,
    val maxQuality: VideoQualityDto? = null,
    val encodeSettings: VideoEncodeSettingsDto? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

