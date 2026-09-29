package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.video.VideoInfo

/**
 * What a status [JobRepository.transition] writes besides the status.
 *
 * - [phase] and [progress]: the progress of the job after the write; no [phase] clears it (a finished
 *   job, a job back in the queue).
 * - [errorMessage]: recorded when given; otherwise the stored error stays, except on a move to
 *   [JobStatus.PENDING], which always clears it.
 * - [videoInfo]: replaces the stored video info when given (the actual format after a download).
 * - [newAttempt]: `attempt = attempt + 1` in the same statement — a retry. A job put back into the queue
 *   by a shutdown or a restart keeps its attempt.
 *
 * A move to [JobStatus.PENDING] also clears `started_at` and `finished_at`.
 */
data class JobStatusPatch(
    val phase: JobPhase? = null,
    val progress: Int? = null,
    val errorMessage: String? = null,
    val videoInfo: VideoInfo? = null,
    val newAttempt: Boolean = false,
) {
    init {
        require(progress == null || progress in 0..100) { "Progress must be 0..100" }
    }
}
