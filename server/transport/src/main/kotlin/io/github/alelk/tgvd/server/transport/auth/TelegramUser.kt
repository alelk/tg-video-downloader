package io.github.alelk.tgvd.server.transport.auth

import io.github.alelk.tgvd.domain.common.TelegramUserId

data class TelegramUser(
    val id: TelegramUserId,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null,
)

