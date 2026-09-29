package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/**
 * Puts a failed or cancelled job of the workspace [workspaceSlug] back to pending as a new attempt
 * (`attempt + 1`). A job of another workspace is [DomainError.JobNotFound].
 *
 * The write is a compare-and-set from the retryable statuses: a job that was retried concurrently is
 * [DomainError.JobCannotBeRetried] with its actual status, and it is not touched.
 */
class RetryJobUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val jobRepository: JobRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        jobId: JobId,
    ): Either<DomainError, Job> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            val job = jobRepository.findInWorkspace(jobId, workspace.id).bind()
            ensure(job.status.isRetryable) { DomainError.JobCannotBeRetried(jobId, job.status) }
            jobRepository
                .transition(
                    jobId,
                    expected = JobStatus.entries.filterTo(mutableSetOf()) { it.isRetryable },
                    to = JobStatus.PENDING,
                    patch = JobStatusPatch(newAttempt = true),
                ).mapLeft { error ->
                    if (error is DomainError.JobStatusConflict) {
                        DomainError.JobCannotBeRetried(jobId, error.actualStatus)
                    } else {
                        error
                    }
                }.bind()
        }
    }
}
