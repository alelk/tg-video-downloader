package io.github.alelk.tgvd.api.contract.job

import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import kotlinx.serialization.Serializable

@Serializable
data class CreateJobRequestDto(
    val source: VideoSourceDto,
    val ruleId: String? = null,
    val category: String,
    val videoInfo: VideoInfoDto,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val saveAsRule: SaveAsRuleDto? = null,
)

