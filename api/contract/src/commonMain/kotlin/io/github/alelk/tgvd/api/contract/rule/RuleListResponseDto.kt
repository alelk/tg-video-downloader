package io.github.alelk.tgvd.api.contract.rule

import kotlinx.serialization.Serializable

@Serializable
data class RuleListResponseDto(
    val items: List<RuleDto>,
)

