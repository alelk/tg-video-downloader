package io.github.alelk.tgvd.api.contract.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val role: String,
    val createdAt: String,
)
