package io.github.alelk.tgvd.api.contract.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiErrorDto(
    val error: ErrorDetail,
) {
    @Serializable
    data class ErrorDetail(
        val code: String,
        val message: String,
        val correlationId: String,
        val details: JsonElement? = null,
    )
}

