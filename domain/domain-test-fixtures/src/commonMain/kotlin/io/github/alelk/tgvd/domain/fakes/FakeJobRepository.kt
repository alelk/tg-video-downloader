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
import kotlin.time.Clock

/**
 * In-memory [JobRepository] with the PostgreSQL adapter's contract: lists are newest first
 * ([findActive] oldest first), [findByVideoId] is scoped to the workspace, and [updateStatus] resets
 * progress and bumps `attempt` on a move back to [JobStatus.PENDING], stamps `startedAt`/`finishedAt`,
 * and reports an unknown id as [DomainError.JobNotFound].
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

    override suspend fun updateStatus(
        id: JobId,
        status: JobStatus,
        phase: JobPhase?,
        progress: Int?,
        errorMessage: String?,
    ): Either<DomainError, Job> {
        val job = jobs[id] ?: return DomainError.JobNotFound(id).left()
        val now = clock.now()
        val updated =
            if (status == JobStatus.PENDING) {
                job.copy(
                    status = status,
                    attempt = job.attempt + 1,
                    phase = null,
                    progress = null,
                    errorMessage = null,
                    startedAt = null,
                    finishedAt = null,
                    updatedAt = now,
                )
            } else {
                val starts = status == JobStatus.DOWNLOADING && phase == JobPhase.DOWNLOAD
                job.copy(
                    status = status,
                    phase = phase,
                    progress = phase?.let { progress ?: 0 },
                    errorMessage = errorMessage ?: job.errorMessage,
                    updatedAt = now,
                    startedAt = if (starts) now else job.startedAt,
                    finishedAt = if (status.isTerminal) now else job.finishedAt,
                )
            }
        jobs[id] = updated
        return updated.right()
    }
}
