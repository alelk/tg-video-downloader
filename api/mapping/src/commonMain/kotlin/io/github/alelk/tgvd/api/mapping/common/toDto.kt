package io.github.alelk.tgvd.api.mapping.common

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.domain.common.Category

fun Category.toDto(): CategoryDto = when (this) {
    Category.MUSIC_VIDEO -> CategoryDto.MUSIC_VIDEO
    Category.SERIES -> CategoryDto.SERIES_EPISODE
    Category.OTHER -> CategoryDto.OTHER
}