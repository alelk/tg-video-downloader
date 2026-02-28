package io.github.alelk.tgvd.server.infra.config

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int = 10,
    val minIdle: Int = 2,
)

