package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.tx.TransactionRunner

class RetryJobUseCase(
    private val jobRepository: JobRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(jobId: JobId): Either<DomainError, Job> =
        txRunner.inRwTransaction {
            either {
                val job = jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
                ensure(job.status.isRetryable) { DomainError.JobCannotBeRetried(jobId, job.status) }
                jobRepository.updateStatus(jobId, JobStatus.PENDING).bind()
            }
        }
}

