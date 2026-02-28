package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.WorkspaceId

interface JobRepository {
    suspend fun findById(id: JobId): Job?
    suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Job>
    suspend fun findActive(): List<Job>
    suspend fun save(job: Job): Either<DomainError, Job>
    suspend fun updateStatus(id: JobId, status: JobStatus, phase: JobPhase? = null, progress: Int? = null, errorMessage: String? = null): Either<DomainError, Job>
}
