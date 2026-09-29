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
 * Cancels a pending or running job of the workspace [workspaceSlug].
 * A job of another workspace is [DomainError.JobNotFound].
 */
class CancelJobUseCase(
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
            ensure(job.status.isCancellable) { DomainError.JobCannotBeCancelled(jobId, job.status) }
            jobRepository.updateStatus(jobId, JobStatus.CANCELLED).bind()
        }
    }
}
