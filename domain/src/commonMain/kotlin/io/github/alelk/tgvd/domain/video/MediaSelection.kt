package io.github.alelk.tgvd.domain.video

/** Null lists inherit server settings; an empty subtitle list explicitly disables subtitles. */
data class MediaSelection(
    val audioFormatIds: List<String>? = null,
    val subtitleLanguages: List<String>? = null,
)
