package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.FilePath

data class OutputTarget(
    val path: FilePath,
    val format: OutputFormat,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

data class StoragePlan(
    val original: OutputTarget,
    val additional: List<OutputTarget> = emptyList(),
) {
    val allTargets: List<OutputTarget> get() = listOf(original) + additional
}
