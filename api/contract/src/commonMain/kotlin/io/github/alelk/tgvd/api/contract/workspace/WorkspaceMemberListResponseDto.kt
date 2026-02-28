package io.github.alelk.tgvd.api.contract.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceMemberListResponseDto(
    val items: List<WorkspaceMemberDto>,
)
