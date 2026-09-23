package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaSelectionPm(
    val audioFormatIds: List<String>? = null,
    val subtitleLanguages: List<String>? = null,
)
