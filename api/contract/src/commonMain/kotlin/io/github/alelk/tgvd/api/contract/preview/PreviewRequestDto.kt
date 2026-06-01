package io.github.alelk.tgvd.api.contract.preview

import kotlinx.serialization.Serializable

@Serializable
data class PreviewRequestDto(
    val url: String,
    val overrides: UserOverridesDto? = null,
    /**
     * When true — bypass the video-info cache and force a fresh extraction via yt-dlp.
     * The new result is written back to the cache, replacing the old entry.
     */
    val force: Boolean = false,
)

