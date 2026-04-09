package io.github.alelk.tgvd.api.mapping.rule

import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.buildSaveAsRuleRequest
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.RuleMatch

/**
 * Adapter between transport layer (api:mapping) and domain [buildSaveAsRuleRequest].
 * Keeps transport layer free from direct domain rule construction logic.
 */
fun buildSaveAsRuleRequest(
    workspaceId: WorkspaceId,
    match: RuleMatch,
    job: Job,
    includeCategory: Boolean = true,
    includeMetadataTemplate: Boolean = true,
    includeStoragePolicy: Boolean = true,
    enabled: Boolean = true,
): CreateRuleRequest =
    buildSaveAsRuleRequest(
        workspaceId = workspaceId,
        match = match,
        job = job,
        includeMetadataTemplate = includeMetadataTemplate,
        includeStoragePolicy = includeStoragePolicy,
        enabled = enabled,
    )