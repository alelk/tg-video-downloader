package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class PostProcessPolicyDto(
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
)

