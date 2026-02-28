package io.github.alelk.tgvd.api.contract.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceMemberDto(
    val userId: Long,
    val role: String,
    val joinedAt: String,
)
