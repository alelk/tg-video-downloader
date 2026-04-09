package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import kotlin.time.Instant

data class JobOutput(
    val jobId: JobId,
    val format: String,
    val path: FilePath,
    val sizeBytes: Long? = null,
    val createdAt: Instant,
)

