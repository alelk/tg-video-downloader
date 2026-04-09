package io.github.alelk.tgvd.domain.storage

data class StoragePlan(
    val original: OutputTarget,
    val additional: List<OutputTarget> = emptyList(),
) {
    val allTargets: List<OutputTarget> get() = listOf(original) + additional
}
