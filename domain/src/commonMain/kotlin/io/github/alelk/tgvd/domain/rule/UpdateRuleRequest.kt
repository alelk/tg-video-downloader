package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputRule

/**
 * Patch-style request: only non-null fields overwrite the existing rule.
 * This allows partial updates without forcing callers to re-supply unchanged fields.
 */
data class UpdateRuleRequest(
    val name: String? = null,
    val match: RuleMatch? = null,
    val metadataTemplate: MetadataTemplate? = null,
    val downloadPolicy: DownloadPolicy? = null,
    val outputs: List<OutputRule>? = null,
    val enabled: Boolean? = null,
    val priority: Int? = null,
)

