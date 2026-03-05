package io.github.alelk.tgvd.server.infra.config

data class TelegramConfig(
    val botToken: String,
    val allowedUserIds: List<String> = emptyList(),
    val allowedUsernames: List<String> = emptyList(),
    val devMode: Boolean = false,
)

