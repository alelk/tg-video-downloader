package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackPreferencesPm(
    val audioLanguages: List<String>? = null,
    val downloadSubtitles: Boolean? = null,
    val subtitleLanguages: List<String>? = null,
)
