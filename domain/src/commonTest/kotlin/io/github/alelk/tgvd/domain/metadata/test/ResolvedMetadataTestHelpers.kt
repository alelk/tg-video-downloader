package io.github.alelk.tgvd.domain.metadata.test

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata

fun ResolvedMetadata.resolvedCategory(): Category = when (this) {
    is ResolvedMetadata.MusicVideo -> Category.MUSIC_VIDEO
    is ResolvedMetadata.SeriesEpisode -> Category.SERIES
    is ResolvedMetadata.Other -> Category.OTHER
}
