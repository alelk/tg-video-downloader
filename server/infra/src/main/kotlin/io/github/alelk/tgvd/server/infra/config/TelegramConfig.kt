package io.github.alelk.tgvd.server.infra.config

data class TelegramConfig(
    val botToken: String,
    val allowedUserIds: List<String>,
    val devMode: Boolean = false,
)

