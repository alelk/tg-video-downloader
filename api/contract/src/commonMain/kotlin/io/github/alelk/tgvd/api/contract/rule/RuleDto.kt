package io.github.alelk.tgvd.api.contract.rule

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import kotlinx.serialization.Serializable

@Serializable
data class RuleDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    val match: RuleMatchDto,
    val category: CategoryDto,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val outputs: List<OutputRuleDto>,
    val createdAt: String,
    val updatedAt: String,
)
