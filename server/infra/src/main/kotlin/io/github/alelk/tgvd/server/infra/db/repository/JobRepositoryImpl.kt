package io.github.alelk.tgvd.server.infra.db.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.server.infra.db.catchingDb
import io.github.alelk.tgvd.server.infra.db.mapping.categoryDbString
import io.github.alelk.tgvd.server.infra.db.mapping.toDbString
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toJobPhase
import io.github.alelk.tgvd.server.infra.db.mapping.toJobStatus
import io.github.alelk.tgvd.server.infra.db.mapping.toMetadataSource
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.mapping.toVideoInfoPm
import io.github.alelk.tgvd.server.infra.db.model.JobErrorPm
import io.github.alelk.tgvd.server.infra.db.model.JobProgressPm
import io.github.alelk.tgvd.server.infra.db.model.MediaSelectionPm
import io.github.alelk.tgvd.server.infra.db.table.JobsTable
import io.github.alelk.tgvd.server.infra.db.violatesUnique
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

/** Partial unique index: one active job per video (V3). */
private const val ACTIVE_VIDEO_UNIQUE_INDEX = "idx_jobs_active_video"

/** Stored values of the non-terminal statuses — through the status mapping, never as literals. */
private val ACTIVE_STATUSES: List<String> = JobStatus.entries.filterNot { it.isTerminal }.map { it.toDbString() }

/**
 * Runs in the transaction of the caller (`TransactionRunner`); never opens one.
 * An insert stores the timestamps of the [Job]; status updates are stamped with [clock].
 */
@OptIn(ExperimentalUuidApi::class)
class JobRepositoryImpl(private val clock: Clock) : JobRepository {
    override suspend fun findById(id: JobId): Job? = JobsTable.selectAll()
        .where { JobsTable.id eq id.value }
        .singleOrNull()
        ?.toJob()

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Job> = JobsTable.selectAll()
        .where { JobsTable.workspaceId eq workspaceId.value }
        .orderBy(JobsTable.createdAt, SortOrder.DESC)
        .map { it.toJob() }

    override suspend fun findByVideoId(videoId: String, workspaceId: WorkspaceId): List<Job> = JobsTable.selectAll()
        .where { (JobsTable.videoId eq videoId) and (JobsTable.workspaceId eq workspaceId.value) }
        .orderBy(JobsTable.createdAt, SortOrder.DESC)
        .map { it.toJob() }

    override suspend fun findActive(): List<Job> = JobsTable.selectAll()
        .where { JobsTable.status inList ACTIVE_STATUSES }
        .orderBy(JobsTable.createdAt, SortOrder.ASC)
        .map { it.toJob() }

    /**
     * Inserts or updates [job]. A second active job for the same video — possible only when a concurrent
     * transaction inserted one after the caller's check — hits the partial unique index and is
     * [DomainError.JobAlreadyExists], as the check in the use-case would have reported it.
     */
    override suspend fun save(job: Job): Either<DomainError, Job> = catchingDb(
        onUniqueViolation = { e ->
            if (e.violatesUnique(ACTIVE_VIDEO_UNIQUE_INDEX)) activeJobConflict(job) else null
        },
    ) {
        val exists = JobsTable.selectAll()
            .where { JobsTable.id eq job.id.value }
            .count() > 0

        if (exists) {
            JobsTable.update({ JobsTable.id eq job.id.value }) {
                it.writeContent(job)
                it[progress] = job.phase?.let { phase ->
                    JobProgressPm(phase = phase.toDbString(), percent = job.progress ?: 0)
                }
                it[JobsTable.error] = job.errorMessage?.let { msg ->
                    JobErrorPm(code = "ERROR", message = msg)
                }
                it[updatedAt] = job.updatedAt
                it[startedAt] = job.startedAt
                it[finishedAt] = job.finishedAt
            }
        } else {
            JobsTable.insert {
                it[id] = job.id.value
                it.writeContent(job)
                it[progress] = null
                it[JobsTable.error] = null
                it[createdAt] = job.createdAt
                it[updatedAt] = job.updatedAt
            }
        }
        job.right()
    }

    override suspend fun updateStatus(
        id: JobId,
        status: JobStatus,
        phase: JobPhase?,
        progress: Int?,
        errorMessage: String?,
    ): Either<DomainError, Job> = catchingDb {
        val timestamp = clock.now()

        // On retry (PENDING): increment attempt first, then update status
        if (status == JobStatus.PENDING) {
            val currentAttempt = JobsTable.selectAll()
                .where { JobsTable.id eq id.value }
                .singleOrNull()
                ?.get(JobsTable.attempt) ?: 0
            JobsTable.update({ JobsTable.id eq id.value }) {
                it[JobsTable.attempt] = currentAttempt + 1
                it[JobsTable.status] = status.toDbString()
                it[JobsTable.progress] = null
                it[JobsTable.error] = null
                it[startedAt] = null
                it[finishedAt] = null
                it[updatedAt] = timestamp
            }
        } else {
            JobsTable.update({ JobsTable.id eq id.value }) {
                it[JobsTable.status] = status.toDbString()
                it[JobsTable.progress] = phase?.let { p ->
                    JobProgressPm(phase = p.toDbString(), percent = progress ?: 0)
                }
                if (errorMessage != null) {
                    it[JobsTable.error] = JobErrorPm(code = "ERROR", message = errorMessage, retryable = false)
                }
                it[updatedAt] = timestamp
                if (status == JobStatus.DOWNLOADING && phase == JobPhase.DOWNLOAD) {
                    it[startedAt] = timestamp
                }
                if (status.isTerminal) {
                    it[finishedAt] = timestamp
                }
            }
        }
        findById(id)?.right() ?: DomainError.JobNotFound(id).left()
    }

    /** Columns that insert and update write alike. */
    private fun UpdateBuilder<*>.writeContent(job: Job) {
        this[JobsTable.workspaceId] = job.workspaceId.value
        this[JobsTable.status] = job.status.toDbString()
        this[JobsTable.videoId] = job.source.videoId.value
        this[JobsTable.sourceUrl] = job.source.url.value
        this[JobsTable.sourceExtractor] = job.source.extractor.value
        this[JobsTable.ruleId] = job.ruleId?.value
        this[JobsTable.category] = job.metadata.categoryDbString()
        this[JobsTable.rawInfo] = job.videoInfo?.toPm() ?: job.source.toVideoInfoPm(job.metadata)
        this[JobsTable.metadata] = job.metadata.toPm()
        this[JobsTable.storagePlan] = job.storagePlan.toPm()
        this[JobsTable.mediaSelection] = job.mediaSelection?.let { selection ->
            MediaSelectionPm(selection.audioFormatIds, selection.subtitleLanguages)
        }
        this[JobsTable.metadataSource] = job.metadataSource.toDbString()
        this[JobsTable.attempt] = job.attempt
        this[JobsTable.createdByTelegramUserId] = job.createdBy.value
    }

    /** Runs after the rollback of the failed insert: reads the active job that won the race. */
    private fun activeJobConflict(job: Job): DomainError? = JobsTable.selectAll()
        .where { (JobsTable.videoId eq job.source.videoId.value) and (JobsTable.status inList ACTIVE_STATUSES) }
        .firstOrNull()
        ?.let { DomainError.JobAlreadyExists(job.source.videoId, JobId(it[JobsTable.id].value)) }

    private fun ResultRow.toJob(): Job = Job(
        id = JobId(this[JobsTable.id].value),
        workspaceId = WorkspaceId(this[JobsTable.workspaceId].value),
        createdBy = TelegramUserId(this[JobsTable.createdByTelegramUserId]),
        source = VideoSource(
            url = Url(this[JobsTable.sourceUrl]),
            videoId = VideoId(this[JobsTable.videoId]),
            extractor = Extractor(this[JobsTable.sourceExtractor]),
        ),
        metadata = this[JobsTable.metadata].toDomain(),
        videoInfo = this[JobsTable.rawInfo].toDomain(),
        metadataSource = this[JobsTable.metadataSource].toMetadataSource(),
        storagePlan = this[JobsTable.storagePlan].toDomain(),
        mediaSelection = this[JobsTable.mediaSelection]?.let {
            MediaSelection(it.audioFormatIds, it.subtitleLanguages)
        },
        ruleId = this[JobsTable.ruleId]?.value?.let { RuleId(it) },
        status = this[JobsTable.status].toJobStatus(),
        phase = this[JobsTable.progress]?.phase?.toJobPhase(),
        progress = this[JobsTable.progress]?.percent,
        errorMessage = this[JobsTable.error]?.message,
        attempt = this[JobsTable.attempt],
        createdAt = this[JobsTable.createdAt],
        updatedAt = this[JobsTable.updatedAt],
        startedAt = this[JobsTable.startedAt],
        finishedAt = this[JobsTable.finishedAt],
    )
}
