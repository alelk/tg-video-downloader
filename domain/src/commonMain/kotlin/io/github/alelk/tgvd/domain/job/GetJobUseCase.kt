package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/** Reads one job of the workspace [workspaceSlug]; a job of another workspace is [DomainError.JobNotFound]. */
class GetJobUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val jobRepository: JobRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        jobId: JobId,
    ): Either<DomainError, Job> = txRunner.inRoTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            jobRepository.findInWorkspace(jobId, workspace.id).bind()
        }
    }
}
