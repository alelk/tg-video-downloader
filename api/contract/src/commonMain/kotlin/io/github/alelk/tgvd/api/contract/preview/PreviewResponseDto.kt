package io.github.alelk.tgvd.api.contract.preview

import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import kotlinx.serialization.Serializable

@Serializable
data class PreviewResponseDto(
    val source: VideoSourceDto,
    val videoInfo: VideoInfoDto,
    val matchedRule: RuleSummaryDto? = null,
    val metadataSource: MetadataSourceDto,
    val category: String,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val warnings: List<String> = emptyList(),
)

