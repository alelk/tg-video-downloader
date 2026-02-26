package io.github.alelk.tgvd.domain.storage

data class PostProcessPolicy(
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
)
