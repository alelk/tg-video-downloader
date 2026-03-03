package io.github.alelk.tgvd.api.contract.rule

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import kotlinx.serialization.Serializable

@Serializable
data class CreateRuleRequestDto(
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val match: RuleMatchDto,
    val category: CategoryDto,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val outputs: List<OutputRuleDto>,
)
