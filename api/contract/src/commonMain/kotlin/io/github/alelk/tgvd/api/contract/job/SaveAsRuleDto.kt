package io.github.alelk.tgvd.api.contract.job

import kotlinx.serialization.Serializable

@Serializable
data class SaveAsRuleDto(
    val enabled: Boolean = true,
    val matchBy: String = "channelId",
    val includeCategory: Boolean = true,
    val includeMetadataTemplate: Boolean = true,
    val includeStoragePolicy: Boolean = true,
)

