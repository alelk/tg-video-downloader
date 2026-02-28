package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobStatus

internal fun JobStatus.toDbString(): String = when (this) {
    JobStatus.PENDING -> "queued"
    JobStatus.DOWNLOADING -> "running"
    JobStatus.POST_PROCESSING -> "post-processing"
    JobStatus.COMPLETED -> "done"
    JobStatus.FAILED -> "failed"
    JobStatus.CANCELLED -> "cancelled"
}

internal fun String.toJobStatus(): JobStatus = when (this) {
    "queued" -> JobStatus.PENDING
    "running" -> JobStatus.DOWNLOADING
    "post-processing" -> JobStatus.POST_PROCESSING
    "done" -> JobStatus.COMPLETED
    "failed" -> JobStatus.FAILED
    "cancelled" -> JobStatus.CANCELLED
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

