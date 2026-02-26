package io.github.alelk.tgvd.api.contract.job

import kotlinx.serialization.Serializable

@Serializable
data class JobProgressDto(
    val phase: String,
    val percent: Int,
    val message: String? = null,
)

