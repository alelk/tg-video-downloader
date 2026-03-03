package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadPolicyPm(
    val maxQuality: String = "best",
    val preferredContainer: String? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
    val writeThumbnail: Boolean = false,
)

