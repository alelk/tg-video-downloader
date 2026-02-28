package io.github.alelk.tgvd.server.infra.config

data class LoggingConfig(
    val level: String = "INFO",
    val format: String = "JSON",
)

