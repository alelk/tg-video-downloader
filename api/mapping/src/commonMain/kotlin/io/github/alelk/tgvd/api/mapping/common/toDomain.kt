package io.github.alelk.tgvd.api.mapping.common

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.domain.common.Category

fun CategoryDto.toDomain(): Category = when (this) {
    CategoryDto.MUSIC_VIDEO -> Category.MUSIC_VIDEO
    CategoryDto.SERIES_EPISODE -> Category.SERIES
    CategoryDto.OTHER -> Category.OTHER
}