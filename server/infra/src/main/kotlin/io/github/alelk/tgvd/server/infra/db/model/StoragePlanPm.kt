package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class StoragePlanPm(
    val original: OutputTargetPm,
    val additional: List<OutputTargetPm> = emptyList(),
)

@Serializable
data class OutputTargetPm(
    val path: String,
    val format: String,
)

