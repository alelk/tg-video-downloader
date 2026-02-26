package io.github.alelk.tgvd.api.contract.storage

import kotlinx.serialization.Serializable

@Serializable
data class StoragePlanDto(
    val original: OutputTargetDto,
    val additional: List<OutputTargetDto> = emptyList(),
)

