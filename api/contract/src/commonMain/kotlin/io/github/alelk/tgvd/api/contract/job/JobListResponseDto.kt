package io.github.alelk.tgvd.api.contract.job

import kotlinx.serialization.Serializable

@Serializable
data class JobListResponseDto(
    val items: List<JobDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

