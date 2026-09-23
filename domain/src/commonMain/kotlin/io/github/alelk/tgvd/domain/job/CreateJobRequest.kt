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
import io.github.alelk.tgvd.domain.video.MediaSelection
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class CreateJobRequest(
    val workspaceId: WorkspaceId,
    val source: VideoSource,
    val videoInfo: VideoInfo? = null,
    val ruleId: RuleId? = null,
    val metadata: ResolvedMetadata,
    val metadataSource: MetadataSource,
    val storagePlan: StoragePlan,
    val createdBy: TelegramUserId,
    val mediaSelection: MediaSelection? = null,
)

/** Creates a new [Job] from this request, assigning a random id and the given timestamps. */
@OptIn(ExperimentalUuidApi::class)
fun CreateJobRequest.toJob(createdAt: Instant, updatedAt: Instant = createdAt): Job =
    Job(
        id = JobId(Uuid.random()),
        workspaceId = workspaceId,
        createdBy = createdBy,
        source = source,
        videoInfo = videoInfo,
        metadata = metadata,
        metadataSource = metadataSource,
        storagePlan = storagePlan,
        ruleId = ruleId,
        mediaSelection = mediaSelection,
        status = JobStatus.PENDING,
        phase = null,
        progress = null,
        errorMessage = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
