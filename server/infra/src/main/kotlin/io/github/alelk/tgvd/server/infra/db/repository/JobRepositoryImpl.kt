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
import io.github.alelk.tgvd.domain.job.JobStatusPatch
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
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

/** Partial unique index: one active job per video (V3). */
private const val ACTIVE_VIDEO_UNIQUE_INDEX = "idx_jobs_active_video"

/** Stored values of the non-terminal statuses — through the status mapping, never as literals. */
private val ACTIVE_STATUSES: List<String> = JobStatus.entries.filterNot { it.isTerminal }.map { it.toDbString() }

/** Stored values of the statuses the processor works in (`downloading`, `post-processing`). */
private val PROCESSING_STATUSES: List<String> = JobStatus.entries.filter { it.isProcessing }.map { it.toDbString() }

private val PENDING: String = JobStatus.PENDING.toDbString()

/**
 * Runs in the transaction of the caller (`TransactionRunner`); never opens one.
 * An insert stores the timestamps of the [Job]; status writes are stamped with [clock].
 * Status writes are conditional: [transition] is a compare-and-set, [claimNext] skips locked rows,
 * [requeueInterrupted] touches only processing rows.
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

    /**
     * One `UPDATE … WHERE id = ? AND status IN (expected)`: the status check and the write are a single
     * statement, so a concurrent move (a cancel committed while the processor reports progress) makes
     * this write match no row instead of overwriting it. Only then the row is read, to report why.
     */
    override suspend fun transition(
        id: JobId,
        expected: Set<JobStatus>,
        to: JobStatus,
        patch: JobStatusPatch,
    ): Either<DomainError, Job> = catchingDb {
        val timestamp = clock.now()
        val requeued = to == JobStatus.PENDING
        val updated =
            JobsTable.update({
                (JobsTable.id eq id.value) and (JobsTable.status inList expected.map { it.toDbString() })
            }) {
                it[status] = to.toDbString()
                it[progress] = patch.phase?.let { phase ->
                    JobProgressPm(phase = phase.toDbString(), percent = patch.progress ?: 0)
                }
                val error = patch.errorMessage?.let { message ->
                    JobErrorPm(code = "ERROR", message = message, retryable = false)
                }
                if (requeued || error != null) it[JobsTable.error] = error
                patch.videoInfo?.let { info -> it[rawInfo] = info.toPm() }
                if (patch.newAttempt) it.update(attempt, attempt + 1)
                it[updatedAt] = timestamp
                when {
                    requeued -> {
                        it[startedAt] = null
                        it[finishedAt] = null
                    }
                    to.isTerminal -> it[finishedAt] = timestamp
                }
            }
        if (updated == 0) {
            val actual = findById(id) ?: return@catchingDb DomainError.JobNotFound(id).left()
            return@catchingDb DomainError.JobStatusConflict(id, actual.status).left()
        }
        findById(id)?.right() ?: DomainError.JobNotFound(id).left()
    }

    /**
     * `SELECT … WHERE status = 'pending' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED`, then the
     * update of that locked row (partial index `idx_jobs_pending` from V3 serves the select). A row locked
     * by a concurrent claim is skipped rather than waited for, so each claim gets a different job or none.
     */
    override suspend fun claimNext(): Job? {
        val next =
            JobsTable
                .select(JobsTable.id)
                .where { JobsTable.status eq PENDING }
                .orderBy(JobsTable.createdAt, SortOrder.ASC)
                .limit(1)
                .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
                .singleOrNull()
                ?.get(JobsTable.id)
                ?: return null
        val timestamp = clock.now()
        val claimed =
            JobsTable.update({ (JobsTable.id eq next) and (JobsTable.status eq PENDING) }) {
                it[status] = JobStatus.DOWNLOADING.toDbString()
                it[progress] = JobProgressPm(phase = JobPhase.DOWNLOAD.toDbString(), percent = 0)
                it[startedAt] = timestamp
                it[updatedAt] = timestamp
            }
        // The row is locked by this transaction, so the guard always matches; it stays as a second line.
        return if (claimed == 1) findById(JobId(next.value)) else null
    }

    override suspend fun requeueInterrupted(): Int = JobsTable.update({ JobsTable.status inList PROCESSING_STATUSES }) {
        it[status] = PENDING
        it[progress] = null
        it[startedAt] = null
        it[updatedAt] = clock.now()
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
