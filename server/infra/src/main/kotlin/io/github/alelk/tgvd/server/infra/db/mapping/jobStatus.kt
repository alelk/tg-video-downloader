package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobStatus

internal fun JobStatus.toDbString(): String = when (this) {
    JobStatus.PENDING -> "pending"
    JobStatus.DOWNLOADING -> "downloading"
    JobStatus.POST_PROCESSING -> "post-processing"
    JobStatus.COMPLETED -> "completed"
    JobStatus.FAILED -> "failed"
    JobStatus.CANCELLED -> "cancelled"
}

internal fun String.toJobStatus(): JobStatus = when (this) {
    "pending" -> JobStatus.PENDING
    "downloading" -> JobStatus.DOWNLOADING
    "post-processing" -> JobStatus.POST_PROCESSING
    "completed" -> JobStatus.COMPLETED
    "failed" -> JobStatus.FAILED
    "cancelled" -> JobStatus.CANCELLED
    // Legacy values (pre-V3 migration, kept for safety)
    "queued" -> JobStatus.PENDING
    "running" -> JobStatus.DOWNLOADING
    "done" -> JobStatus.COMPLETED
    else -> error("Unknown job status: $this")
}

internal fun JobPhase.toDbString(): String = name.lowercase()

internal fun String.toJobPhase(): JobPhase? = when (this) {
    "download" -> JobPhase.DOWNLOAD
    "convert" -> JobPhase.CONVERT
    "embed_metadata" -> JobPhase.EMBED_METADATA
    "embed_thumbnail" -> JobPhase.EMBED_THUMBNAIL
    "normalize_audio" -> JobPhase.NORMALIZE_AUDIO
    "move_file" -> JobPhase.MOVE_FILE
    else -> null
}

