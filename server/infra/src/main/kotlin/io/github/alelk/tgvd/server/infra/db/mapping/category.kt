package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.metadata.category

internal fun Category.toDbString(): String = when (this) {
    Category.MUSIC_VIDEO -> "music-video"
    Category.SERIES -> "series-episode"
    Category.OTHER -> "other"
}

internal fun String.toCategory(): Category = when (this) {
    "music-video" -> Category.MUSIC_VIDEO
    "series-episode" -> Category.SERIES
    "other" -> Category.OTHER
    else -> error("Unknown category: $this")
}

internal fun ResolvedMetadata.categoryDbString(): String = category.toDbString()

internal fun MetadataTemplate.categoryDbString(): String = category.toDbString()

