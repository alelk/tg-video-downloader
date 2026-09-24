package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

/**
 * Optional audio/subtitle track overrides. Every null field inherits from the lower-priority level
 * (global settings → rule → channel).
 */
@Serializable
data class TrackPreferencesDto(
    /** Additional audio languages; empty = source-original track only. */
    val audioLanguages: List<String>? = null,
    /** true/false = force subtitles on/off. */
    val downloadSubtitles: Boolean? = null,
    /** Subtitle languages; empty = inherit. */
    val subtitleLanguages: List<String>? = null,
)
