package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class StoragePolicyPm(
    val originalTemplate: String,
    val additionalOutputs: List<OutputTemplatePm> = emptyList(),
)

@Serializable
data class OutputTemplatePm(
    val pathTemplate: String,
    val format: String,
)

