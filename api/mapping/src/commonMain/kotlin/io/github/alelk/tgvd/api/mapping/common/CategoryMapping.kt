package io.github.alelk.tgvd.api.mapping.common

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.domain.common.Category

fun Category.toDto(): CategoryDto = when (this) {
    Category.MUSIC_VIDEO -> CategoryDto.MUSIC_VIDEO
    Category.SERIES -> CategoryDto.SERIES_EPISODE
    Category.OTHER -> CategoryDto.OTHER
}

fun CategoryDto.toDomain(): Category = when (this) {
    CategoryDto.MUSIC_VIDEO -> Category.MUSIC_VIDEO
    CategoryDto.SERIES_EPISODE -> Category.SERIES
    CategoryDto.OTHER -> Category.OTHER
}

/**
 * Converts [Category] to its API string representation (kebab-case).
 *
 * @deprecated Use [toDto] instead for type-safe mapping.
 */
@Deprecated("Use Category.toDto() instead", ReplaceWith("toDto()"))
val Category.apiString: String
    get() = when (this) {
        Category.MUSIC_VIDEO -> "music-video"
        Category.SERIES -> "series-episode"
        Category.OTHER -> "other"
    }
