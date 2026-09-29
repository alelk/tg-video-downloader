package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What the user asks to download, as confirmed on the preview screen.
 *
 * The workspace and the author are not part of the request: [CreateJobUseCase] takes them from the
 * call (workspace slug and the authenticated user) and checks the membership itself.
 *
 * @param saveAsRule when set, a rule reproducing this job is created next to it.
 */
data class CreateJobRequest(
    val source: VideoSource,
    val videoInfo: VideoInfo,
    val ruleId: RuleId? = null,
    val metadata: ResolvedMetadata,
    val metadataSource: MetadataSource,
    val storagePlan: StoragePlan,
    val mediaSelection: MediaSelection? = null,
    val saveAsRule: SaveAsRule? = null,
)

/**
 * "Save as rule" options of a [CreateJobRequest]: the created rule matches the video's channel and
 * reproduces the job's metadata template and storage plan (see [buildSaveAsRuleRequest]).
 */
data class SaveAsRule(
    val matchBy: MatchBy = MatchBy.CHANNEL_ID,
    val includeMetadataTemplate: Boolean = true,
    val includeStoragePolicy: Boolean = true,
    val enabled: Boolean = true,
) {
    /** Which property of the video's channel the created rule matches on. */
    enum class MatchBy { CHANNEL_ID, CHANNEL_NAME }
}

/** Creates a new [Job] from this request, assigning a random id and the given timestamps. */
@OptIn(ExperimentalUuidApi::class)
fun CreateJobRequest.toJob(
    workspaceId: WorkspaceId,
    createdBy: TelegramUserId,
    createdAt: Instant,
    updatedAt: Instant = createdAt,
): Job = Job(
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
