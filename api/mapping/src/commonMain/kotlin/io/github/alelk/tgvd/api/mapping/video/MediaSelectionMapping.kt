package io.github.alelk.tgvd.api.mapping.video

import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.domain.video.MediaSelection

fun MediaSelectionDto.toDomain(): MediaSelection =
    MediaSelection(audioFormatIds = audioFormatIds, subtitleLanguages = subtitleLanguages)

fun MediaSelection.toDto(): MediaSelectionDto =
    MediaSelectionDto(audioFormatIds = audioFormatIds, subtitleLanguages = subtitleLanguages)
