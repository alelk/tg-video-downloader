package io.github.alelk.tgvd.api.contract.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceListResponseDto(
    val items: List<WorkspaceDto>,
)
