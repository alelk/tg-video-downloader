package io.github.alelk.tgvd.features.common.util

import androidx.compose.runtime.Composable
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.category_music_video
import io.github.alelk.tgvd.features.generated.resources.category_other
import io.github.alelk.tgvd.features.generated.resources.category_series
import org.jetbrains.compose.resources.stringResource

@Composable
fun categoryLabel(category: CategoryDto): String = when (category) {
    CategoryDto.MUSIC_VIDEO -> stringResource(Res.string.category_music_video)
    CategoryDto.SERIES_EPISODE -> stringResource(Res.string.category_series)
    CategoryDto.OTHER -> stringResource(Res.string.category_other)
}

