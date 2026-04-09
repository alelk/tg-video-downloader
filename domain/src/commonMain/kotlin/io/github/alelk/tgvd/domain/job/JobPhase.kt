package io.github.alelk.tgvd.domain.job

enum class JobPhase {
    DOWNLOAD,
    CONVERT,
    EMBED_METADATA,
    EMBED_THUMBNAIL,
    NORMALIZE_AUDIO,
    MOVE_FILE,
}