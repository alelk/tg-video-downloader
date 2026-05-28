package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import kotlin.time.Instant

data class Job(
    val id: JobId,
    val workspaceId: WorkspaceId,
    val createdBy: TelegramUserId,
    val source: VideoSource,
    val videoInfo: VideoInfo? = null,
    val metadata: ResolvedMetadata,
    val metadataSource: MetadataSource,
    val storagePlan: StoragePlan,
    val ruleId: RuleId? = null,
    val status: JobStatus = JobStatus.PENDING,
    val phase: JobPhase? = null,
    val progress: Int? = null,
    val errorMessage: String? = null,
    val attempt: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
) {
    init {
        require(progress == null || progress in 0..100) { "Progress must be 0..100" }
        require(attempt >= 0) { "Attempt must be >= 0" }
    }
}
