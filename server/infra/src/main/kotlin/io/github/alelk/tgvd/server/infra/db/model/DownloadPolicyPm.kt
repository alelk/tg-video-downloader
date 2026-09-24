package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadPolicyPm(
    val maxQuality: String = "best",
    val downloadSubtitles: Boolean? = null,
    val subtitleLanguages: List<String> = emptyList(),
    val writeThumbnail: Boolean = false,
)

