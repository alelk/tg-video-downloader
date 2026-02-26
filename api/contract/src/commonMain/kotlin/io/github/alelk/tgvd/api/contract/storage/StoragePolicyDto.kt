package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class StoragePolicyDto(
    val originalTemplate: String,
    val additionalOutputs: List<OutputTemplateDto> = emptyList(),
)

