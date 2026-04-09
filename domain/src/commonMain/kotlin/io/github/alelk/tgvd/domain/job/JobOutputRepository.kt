package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.JobId

interface JobOutputRepository {
    suspend fun saveAll(outputs: List<JobOutput>)
    suspend fun findByJob(jobId: JobId): List<JobOutput>
}