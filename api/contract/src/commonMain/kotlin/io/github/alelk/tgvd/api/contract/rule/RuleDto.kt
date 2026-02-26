package io.github.alelk.tgvd.api.contract.rule

import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.PostProcessPolicyDto
import io.github.alelk.tgvd.api.contract.storage.StoragePolicyDto
import kotlinx.serialization.Serializable

@Serializable
data class RuleDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    val match: RuleMatchDto,
    val category: String,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val storagePolicy: StoragePolicyDto,
    val postProcessPolicy: PostProcessPolicyDto,
    val createdAt: String,
    val updatedAt: String,
)
