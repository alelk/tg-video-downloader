package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.FilePath

data class OutputTarget(
    val path: FilePath,
    val format: OutputFormat,
)

data class StoragePlan(
    val original: OutputTarget,
    val additional: List<OutputTarget> = emptyList(),
) {
    val allTargets: List<OutputTarget> get() = listOf(original) + additional
}
