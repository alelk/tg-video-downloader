package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.job.JobOutput
import io.github.alelk.tgvd.domain.job.JobOutputRepository
import io.github.alelk.tgvd.server.infra.db.table.JobOutputsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.ExperimentalUuidApi

/** Runs in the transaction of the caller (`TransactionRunner`); never opens one. */
@OptIn(ExperimentalUuidApi::class)
class JobOutputRepositoryImpl : JobOutputRepository {
    override suspend fun saveAll(outputs: List<JobOutput>) {
        if (outputs.isEmpty()) return
        JobOutputsTable.batchInsert(outputs) { output ->
            this[JobOutputsTable.jobId] = output.jobId.value
            this[JobOutputsTable.format] = output.format
            this[JobOutputsTable.path] = output.path.value
            this[JobOutputsTable.size] = output.sizeBytes
            this[JobOutputsTable.createdAt] = output.createdAt
        }
    }

    override suspend fun findByJob(jobId: JobId): List<JobOutput> = JobOutputsTable.selectAll()
        .where { JobOutputsTable.jobId eq jobId.value }
        .map { row ->
            JobOutput(
                jobId = jobId,
                format = row[JobOutputsTable.format],
                path = FilePath(row[JobOutputsTable.path]),
                sizeBytes = row[JobOutputsTable.size],
                createdAt = row[JobOutputsTable.createdAt],
            )
        }
}
