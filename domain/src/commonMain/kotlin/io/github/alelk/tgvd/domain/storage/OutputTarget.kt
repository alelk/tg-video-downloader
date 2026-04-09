package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.FilePath

data class OutputTarget(
    val path: FilePath,
    val format: OutputFormat,
    val maxQuality: DownloadPolicy.VideoQuality? = null,
    val encodeSettings: VideoEncodeSettings? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)