package io.github.alelk.tgvd.domain.job

/**
 * Status of a job and the table of legal transitions between statuses ([targets]).
 *
 * Who moves a job along an edge:
 * - `PENDING → DOWNLOADING` — the processor's atomic claim (`JobRepository.claimNext`);
 * - `DOWNLOADING/POST_PROCESSING → itself, a later phase, COMPLETED, FAILED` — the processor;
 * - `DOWNLOADING/POST_PROCESSING → PENDING` — the processor on shutdown and the start-up recovery
 *   (`JobRepository.requeueInterrupted`), without a new attempt;
 * - `PENDING/DOWNLOADING/POST_PROCESSING → CANCELLED` — only `CancelJobUseCase`;
 * - `FAILED/CANCELLED → PENDING` — only `RetryJobUseCase`, as a new attempt.
 *
 * Every status write is a compare-and-set from a set of expected statuses (`JobRepository.transition`),
 * so a write that lost a race (a cancelled job's late progress) changes nothing.
 */
enum class JobStatus {
    PENDING,
    DOWNLOADING,
    POST_PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    /** The statuses a job in this status may move to. */
    val targets: Set<JobStatus> get() = TRANSITIONS.getValue(this)

    fun canMoveTo(target: JobStatus): Boolean = target in targets

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED

    /** The processor works on the job: the statuses between the claim and the outcome. */
    val isProcessing: Boolean get() = this == DOWNLOADING || this == POST_PROCESSING

    val isCancellable: Boolean get() = canMoveTo(CANCELLED)

    /** A finished job that may start again as a new attempt. */
    val isRetryable: Boolean get() = isTerminal && canMoveTo(PENDING)

    companion object {
        // In the companion, not at file level: a file-level `JobStatusKt` would clash on the JVM with the
        // class of `domain-test-fixtures`' `job/jobStatus.kt` (see Stage 01.7 notes).
        private val TRANSITIONS: Map<JobStatus, Set<JobStatus>> by lazy {
            mapOf(
                JobStatus.PENDING to setOf(JobStatus.DOWNLOADING, JobStatus.CANCELLED),
                JobStatus.DOWNLOADING to
                    setOf(
                        JobStatus.DOWNLOADING,
                        JobStatus.POST_PROCESSING,
                        JobStatus.COMPLETED,
                        JobStatus.FAILED,
                        JobStatus.CANCELLED,
                        JobStatus.PENDING,
                    ),
                JobStatus.POST_PROCESSING to
                    setOf(
                        JobStatus.POST_PROCESSING,
                        JobStatus.COMPLETED,
                        JobStatus.FAILED,
                        JobStatus.CANCELLED,
                        JobStatus.PENDING,
                    ),
                JobStatus.COMPLETED to emptySet(),
                JobStatus.FAILED to setOf(JobStatus.PENDING),
                JobStatus.CANCELLED to setOf(JobStatus.PENDING),
            )
        }

        /** Every status from which [target] may be reached. */
        fun sourcesOf(target: JobStatus): Set<JobStatus> = entries.filterTo(mutableSetOf()) { it.canMoveTo(target) }

        /** The processing statuses from which the processor may write [target] — the expected set of its CAS. */
        fun processingSourcesOf(target: JobStatus): Set<JobStatus> = sourcesOf(target).filterTo(mutableSetOf()) {
            it.isProcessing
        }
    }
}
