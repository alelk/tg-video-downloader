package io.github.alelk.tgvd.api.contract.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MetadataSourceDto {
    @SerialName("rule") RULE,
    @SerialName("llm") LLM,
    @SerialName("fallback") FALLBACK,
}

