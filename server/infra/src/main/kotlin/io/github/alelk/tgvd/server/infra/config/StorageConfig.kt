package io.github.alelk.tgvd.server.infra.config

data class StorageConfig(
    val baseDirectories: List<String>,
    val tempDirectory: String = "/tmp/tgvd",
)

