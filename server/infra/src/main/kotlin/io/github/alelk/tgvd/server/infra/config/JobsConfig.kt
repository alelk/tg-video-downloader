package io.github.alelk.tgvd.server.infra.config

data class JobsConfig(
    val maxConcurrentDownloads: Int = 2,
    val maxAttempts: Int = 3,
    val pollIntervalMs: Long = 5000,
    val retryDelayMs: Long = 30000,
)

