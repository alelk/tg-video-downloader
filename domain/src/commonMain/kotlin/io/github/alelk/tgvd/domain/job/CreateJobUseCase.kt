package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.video.VideoSource
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CreateJobUseCase(
    private val jobRepository: JobRepository,
    private val clock: Clock = Clock.System,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(request: CreateJobRequest): Either<DomainError, Job> = either {
        // Check for existing active jobs with same video
        val activeJobs = jobRepository.findActive()
            .filter { it.source.videoId == request.source.videoId }
        ensure(activeJobs.isEmpty()) {
            DomainError.JobAlreadyExists(request.source.videoId, activeJobs.first().id)
        }

        val now = clock.now()
        val job = Job(
            id = JobId(Uuid.random()),
            workspaceId = request.workspaceId,
            createdBy = request.createdBy,
            source = request.source,
            metadata = request.metadata,
            metadataSource = request.metadataSource,
            storagePlan = request.storagePlan,
            ruleId = request.ruleId,
            status = JobStatus.PENDING,
            phase = null,
            progress = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )

        jobRepository.save(job).bind()
    }

    data class CreateJobRequest(
        val workspaceId: WorkspaceId,
        val source: VideoSource,
        val ruleId: RuleId?,
        val metadata: ResolvedMetadata,
        val metadataSource: MetadataSource,
        val storagePlan: StoragePlan,
        val createdBy: TelegramUserId,
    )
}



