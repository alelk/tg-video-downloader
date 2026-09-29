package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/**
 * One page of [ListJobsUseCase].
 *
 * @param items the jobs of the page, newest first.
 * @param total the number of jobs that match the filter (all pages).
 */
data class JobPage(val items: List<Job>, val total: Int)

/**
 * Lists the jobs of the workspace [workspaceSlug], newest first.
 *
 * The filter and the page are applied in memory over all jobs of the workspace.
 */
class ListJobsUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val jobRepository: JobRepository,
    private val txRunner: TransactionRunner,
) {
    /**
     * @param statusName keeps only jobs whose [JobStatus] name equals it, ignoring case (`"pending"`,
     *   `"POST_PROCESSING"`); an unknown name matches nothing; null = no filter.
     * @param offset the number of matching jobs to skip.
     * @param limit the maximum number of jobs in the page.
     */
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        statusName: String?,
        offset: Int,
        limit: Int,
    ): Either<DomainError, JobPage> = txRunner.inRoTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            val jobs = jobRepository.findByWorkspace(workspace.id)
            val filtered =
                if (statusName == null) {
                    jobs
                } else {
                    jobs.filter { it.status.name.equals(statusName, ignoreCase = true) }
                }
            JobPage(items = filtered.drop(offset).take(limit), total = filtered.size)
        }
    }
}
