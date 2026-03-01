package io.github.alelk.tgvd.domain.job

enum class JobStatus {
    PENDING,
    DOWNLOADING,
    POST_PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
    val isCancellable: Boolean get() = this == PENDING || this == DOWNLOADING || this == POST_PROCESSING
    val isRetryable: Boolean get() = this == FAILED || this == CANCELLED
}

enum class JobPhase {
    DOWNLOAD,
    CONVERT,
    EMBED_METADATA,
    EMBED_THUMBNAIL,
    NORMALIZE_AUDIO,
    MOVE_FILE,
}
