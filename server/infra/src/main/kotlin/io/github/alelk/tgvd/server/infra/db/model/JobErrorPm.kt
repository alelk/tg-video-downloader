package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class JobErrorPm(
    val code: String,
    val message: String,
    val details: String? = null,
    val retryable: Boolean = false,
)

