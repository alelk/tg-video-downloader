package io.github.alelk.tgvd.domain.fakes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.job.JobStatusPatch
import kotlin.time.Clock

/**
 * In-memory [JobRepository] with the PostgreSQL adapter's contract: lists are newest first
 * ([findActive] oldest first), [findByVideoId] is scoped to the workspace, [transition] is a
 * compare-and-set (a job in another status is [DomainError.JobStatusConflict], an unknown id
 * [DomainError.JobNotFound]) with the same patch rules as the SQL `UPDATE`, [claimNext] takes the oldest
 * pending job, and [requeueInterrupted] puts processing jobs back to pending with the same attempt.
 */
class FakeJobRepository(private val clock: Clock) : JobRepository {
    private val jobs = linkedMapOf<JobId, Job>()

    /** All stored jobs, in insertion order. */
    val all: List<Job> get() = jobs.values.toList()

    /** Stores [job] as is and returns it. */
    fun seed(job: Job): Job = job.also { jobs[it.id] = it }

    override suspend fun findById(id: JobId): Job? = jobs[id]

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Job> =
        jobs.values.filter { it.workspaceId == workspaceId }.sortedByDescending { it.createdAt }

    override suspend fun findByVideoId(videoId: String, workspaceId: WorkspaceId): List<Job> = jobs.values
        .filter { it.source.videoId.value == videoId && it.workspaceId == workspaceId }
        .sortedByDescending { it.createdAt }

    override suspend fun findActive(): List<Job> =
        jobs.values.filter { !it.status.isTerminal }.sortedBy { it.createdAt }

    override suspend fun save(job: Job): Either<DomainError, Job> {
        jobs[job.id] = job
        return job.right()
    }

    override suspend fun transition(
        id: JobId,
        expected: Set<JobStatus>,
        to: JobStatus,
        patch: JobStatusPatch,
    ): Either<DomainError, Job> {
        val job = jobs[id] ?: return DomainError.JobNotFound(id).left()
        if (job.status !in expected) return DomainError.JobStatusConflict(id, job.status).left()
        val now = clock.now()
        val requeued = to == JobStatus.PENDING
        val updated =
            job.copy(
                status = to,
                phase = patch.phase,
                progress = patch.phase?.let { patch.progress ?: 0 },
                errorMessage = if (requeued) null else patch.errorMessage ?: job.errorMessage,
                videoInfo = patch.videoInfo ?: job.videoInfo,
                attempt = if (patch.newAttempt) job.attempt + 1 else job.attempt,
                updatedAt = now,
                startedAt = if (requeued) null else job.startedAt,
                finishedAt = if (requeued) {
                    null
                } else if (to.isTerminal) {
                    now
                } else {
                    job.finishedAt
                },
            )
        jobs[id] = updated
        return updated.right()
    }

    override suspend fun claimNext(): Job? {
        val next = jobs.values.filter { it.status == JobStatus.PENDING }.minByOrNull { it.createdAt } ?: return null
        val now = clock.now()
        return next
            .copy(
                status = JobStatus.DOWNLOADING,
                phase = JobPhase.DOWNLOAD,
                progress = 0,
                startedAt = now,
                updatedAt = now,
            ).also { jobs[it.id] = it }
    }

    override suspend fun requeueInterrupted(): Int {
        val interrupted = jobs.values.filter { it.status.isProcessing }
        val now = clock.now()
        interrupted.forEach {
            jobs[it.id] =
                it.copy(
                    status = JobStatus.PENDING,
                    phase = null,
                    progress = null,
                    startedAt = null,
                    updatedAt = now,
                )
        }
        return interrupted.size
    }
}
