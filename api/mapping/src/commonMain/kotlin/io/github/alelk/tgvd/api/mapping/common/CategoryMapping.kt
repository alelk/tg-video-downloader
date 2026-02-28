package io.github.alelk.tgvd.api.mapping.common

import io.github.alelk.tgvd.domain.common.Category

/**
 * Converts [Category] to its API string representation (kebab-case).
 *
 * Used across all DTO mappings for consistent serialization:
 * - `MUSIC_VIDEO` → `"music-video"`
 * - `SERIES` → `"series-episode"`
 * - `OTHER` → `"other"`
 */
val Category.apiString: String
    get() = when (this) {
        Category.MUSIC_VIDEO -> "music-video"
        Category.SERIES -> "series-episode"
        Category.OTHER -> "other"
    }

