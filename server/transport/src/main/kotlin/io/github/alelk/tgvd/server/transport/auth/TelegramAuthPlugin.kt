package io.github.alelk.tgvd.server.transport.auth

import io.github.alelk.tgvd.server.transport.error.apiError
import io.ktor.http.*
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.callid.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

val TelegramUserKey = AttributeKey<TelegramUser>("TelegramUser")

val RoutingCall.telegramUser: TelegramUser
    get() = attributes[TelegramUserKey]

val RoutingCall.telegramUserOrNull: TelegramUser?
    get() = attributes.getOrNull(TelegramUserKey)

val TelegramAuthPlugin = createRouteScopedPlugin(
    name = "TelegramAuth",
    createConfiguration = ::TelegramAuthConfig,
) {
    val validator = pluginConfig.validator
    val allowedUsers = pluginConfig.allowedUserIds

    onCall { call ->
        val correlationId = call.callId ?: "unknown"
        val initData = call.request.headers["X-Telegram-Init-Data"]

        if (initData == null) {
            call.respond(HttpStatusCode.Unauthorized, apiError("UNAUTHORIZED", "Missing X-Telegram-Init-Data header", correlationId))
            return@onCall
        }

        when (val result = validator.validate(initData)) {
            is arrow.core.Either.Left -> {
                call.respond(HttpStatusCode.Unauthorized, apiError("UNAUTHORIZED", "Invalid initData: ${result.value}", correlationId))
            }

            is arrow.core.Either.Right -> {
                val user = result.value
                if (allowedUsers.isNotEmpty() && user.id.value !in allowedUsers) {
                    call.respond(HttpStatusCode.Forbidden, apiError("FORBIDDEN", "User not allowed", correlationId))
                    return@onCall
                }
                call.attributes.put(TelegramUserKey, user)
            }
        }
    }
}

class TelegramAuthConfig {
    lateinit var validator: TelegramAuthValidator
    var allowedUserIds: Set<Long> = emptySet()
}


