package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.preview.UserOverrides
import io.github.alelk.tgvd.domain.video.VideoInfo

data class MatchContext(
    val video: VideoInfo,
    val overrides: UserOverrides? = null,
)

