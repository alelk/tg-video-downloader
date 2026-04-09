package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class CreateJobUseCase(
    private val jobRepository: JobRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(request: CreateJobRequest): Either<DomainError, Job> =
        txRunner.inRwTransaction {
            either {
                // Check for existing active jobs with same video (within transaction to avoid TOCTOU)
                val activeJobs =
                    jobRepository.findActive()
                        .filter { it.source.videoId == request.source.videoId }

                ensure(activeJobs.isEmpty()) {
                    DomainError.JobAlreadyExists(request.source.videoId, activeJobs.first().id)
                }

                val now = clock.now()
                jobRepository.save(request.toJob(now)).bind()
            }
        }
}

