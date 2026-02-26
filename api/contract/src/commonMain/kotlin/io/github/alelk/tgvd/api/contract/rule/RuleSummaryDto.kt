package io.github.alelk.tgvd.api.contract.rule

import kotlinx.serialization.Serializable

@Serializable
data class RuleSummaryDto(
    val id: String,
    val name: String?,
)

