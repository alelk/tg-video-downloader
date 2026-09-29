package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.WorkspaceId

/**
 * Jobs in the transaction of the caller.
 *
 * Status is never written unconditionally: [transition] is a compare-and-set, [claimNext] and
 * [requeueInterrupted] move only rows that are in the expected status at the moment of the write.
 * [save] writes the job's content on insert (and as is on update — no production caller updates a
 * job's status through it).
 */
interface JobRepository {
    suspend fun findById(id: JobId): Job?
    suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Job>
    suspend fun findByVideoId(videoId: String, workspaceId: WorkspaceId): List<Job>
    suspend fun findActive(): List<Job>
    suspend fun save(job: Job): Either<DomainError, Job>

    /**
     * Moves job [id] to [to] only if its current status is one of [expected] — one conditional
     * `UPDATE`. [patch] says what else the write sets (see [JobStatusPatch]); `updated_at` is stamped,
     * `finished_at` too when [to] is terminal.
     *
     * A job in another status is [DomainError.JobStatusConflict] with the actual status and is left
     * untouched; a missing job is [DomainError.JobNotFound].
     */
    suspend fun transition(
        id: JobId,
        expected: Set<JobStatus>,
        to: JobStatus,
        patch: JobStatusPatch = JobStatusPatch(),
    ): Either<DomainError, Job>

    /**
     * Atomically claims the oldest [JobStatus.PENDING] job: moves it to [JobStatus.DOWNLOADING]
     * (phase `DOWNLOAD`, 0 %), stamps `started_at`, and returns it; `null` when nothing is pending.
     * A row another transaction is claiming is skipped (`FOR UPDATE SKIP LOCKED`), so two concurrent
     * claims never return the same job.
     */
    suspend fun claimNext(): Job?

    /**
     * Start-up recovery: every job left in [JobStatus.DOWNLOADING] or [JobStatus.POST_PROCESSING] by a
     * process that is gone goes back to [JobStatus.PENDING] with its progress cleared and the same
     * `attempt`. Returns how many jobs were requeued.
     */
    suspend fun requeueInterrupted(): Int
}
