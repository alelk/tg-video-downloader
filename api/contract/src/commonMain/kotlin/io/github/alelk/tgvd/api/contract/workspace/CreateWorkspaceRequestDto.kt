package io.github.alelk.tgvd.api.contract.workspace

import kotlinx.serialization.Serializable

@Serializable
data class CreateWorkspaceRequestDto(
    val slug: String,
    val name: String,
)
