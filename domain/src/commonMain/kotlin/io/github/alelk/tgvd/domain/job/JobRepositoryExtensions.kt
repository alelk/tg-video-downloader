package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.WorkspaceId

/**
 * The job [id] if it belongs to [workspaceId]. A job of another workspace is reported exactly like a
 * missing one — [DomainError.JobNotFound] — so its existence is not revealed.
 */
internal suspend fun JobRepository.findInWorkspace(id: JobId, workspaceId: WorkspaceId): Either<DomainError, Job> =
    findById(id)?.takeIf { it.workspaceId == workspaceId }?.right() ?: DomainError.JobNotFound(id).left()
