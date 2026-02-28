package io.github.alelk.tgvd.server.infra.db.model

import kotlinx.serialization.Serializable

@Serializable
data class JobProgressPm(
    val phase: String,
    val percent: Int,
    val message: String? = null,
)

