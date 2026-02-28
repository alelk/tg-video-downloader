package io.github.alelk.tgvd.server.infra.config

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val baseUrl: String = "http://localhost:8080",
)

