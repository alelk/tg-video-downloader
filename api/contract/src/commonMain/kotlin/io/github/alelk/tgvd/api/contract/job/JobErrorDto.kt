package io.github.alelk.tgvd.api.contract.job

import kotlinx.serialization.Serializable

@Serializable
data class JobErrorDto(
    val code: String,
    val message: String,
    val details: String? = null,
    val retryable: Boolean = false,
)

