package io.github.alelk.tgvd.server.infra.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class FfmpegConfig(
    val path: String = "ffmpeg",
    val timeout: Duration = 60.minutes,
)

