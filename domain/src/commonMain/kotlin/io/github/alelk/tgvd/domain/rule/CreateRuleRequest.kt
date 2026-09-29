package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputRule
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A new rule; the workspace it belongs to is given by the use-case that creates it. */
data class CreateRuleRequest(
    val name: String,
    val match: RuleMatch,
    val metadataTemplate: MetadataTemplate,
    val downloadPolicy: DownloadPolicy = DownloadPolicy(),
    val outputs: List<OutputRule>,
    val enabled: Boolean = true,
    val priority: Int = 0,
)

/** Creates a new [Rule] of [workspaceId] from this request, assigning a random id and the given timestamps. */
@OptIn(ExperimentalUuidApi::class)
fun CreateRuleRequest.toRule(workspaceId: WorkspaceId, createdAt: Instant, updatedAt: Instant = createdAt): Rule = Rule(
    id = RuleId(Uuid.random()),
    workspaceId = workspaceId,
    name = name,
    match = match,
    metadataTemplate = metadataTemplate,
    downloadPolicy = downloadPolicy,
    outputs = outputs,
    enabled = enabled,
    priority = priority,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
