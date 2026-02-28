package io.github.alelk.tgvd.api.contract.workspace

import kotlinx.serialization.Serializable

@Serializable
data class AddMemberRequestDto(
    val userId: Long,
    val role: String = "member",
)
