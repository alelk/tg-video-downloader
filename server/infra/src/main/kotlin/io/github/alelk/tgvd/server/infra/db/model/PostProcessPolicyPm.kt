package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class PostProcessPolicyPm(
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
)

