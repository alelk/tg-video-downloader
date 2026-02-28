package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.PostProcessPolicy
import io.github.alelk.tgvd.domain.storage.StoragePolicy
import kotlin.time.Instant

data class Rule(
    val id: RuleId,
    val name: String,
    val workspaceId: WorkspaceId,
    val match: RuleMatch,
    val metadataTemplate: MetadataTemplate,
    val storagePolicy: StoragePolicy,
    val downloadPolicy: DownloadPolicy = DownloadPolicy(),
    val postProcessPolicy: PostProcessPolicy = PostProcessPolicy(),
    val enabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Rule name cannot be blank" }
    }
}
