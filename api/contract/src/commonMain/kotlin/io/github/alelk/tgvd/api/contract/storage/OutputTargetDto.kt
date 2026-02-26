package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class OutputTargetDto(
    val path: String,
    val format: String,
)

