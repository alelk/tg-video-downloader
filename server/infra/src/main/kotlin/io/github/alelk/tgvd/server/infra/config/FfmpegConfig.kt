package io.github.alelk.tgvd.server.infra.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class FfmpegConfig(
    val path: String = "ffmpeg",
    val timeout: Duration = 60.minutes,
    /** Explicit render device path (e.g. /dev/dri/renderD128). Null = auto-detect. */
    val renderDevice: String? = null,
)

