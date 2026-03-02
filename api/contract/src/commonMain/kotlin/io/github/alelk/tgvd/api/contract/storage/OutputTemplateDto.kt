package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class OutputTemplateDto(
    val pathTemplate: String,
    val format: OutputFormatDto,
)

