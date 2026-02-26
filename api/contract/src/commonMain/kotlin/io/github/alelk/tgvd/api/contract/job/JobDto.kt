package io.github.alelk.tgvd.api.contract.job

import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import kotlinx.serialization.Serializable

@Serializable
data class JobDto(
    val id: String,
    val status: String,
    val source: VideoSourceDto,
    val ruleId: String? = null,
    val category: String,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val progress: JobProgressDto? = null,
    val error: JobErrorDto? = null,
    val attempt: Int = 1,
    val createdAt: String,
    val updatedAt: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

