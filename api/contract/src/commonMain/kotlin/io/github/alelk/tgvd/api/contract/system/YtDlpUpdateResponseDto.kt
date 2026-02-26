package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class YtDlpUpdateResponseDto(
    val status: String,
    val message: String,
)

