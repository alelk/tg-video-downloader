package io.github.alelk.tgvd.server.infra.config

data class TelegramConfig(
    val botToken: String,
    val allowedUserIds: List<String> = emptyList(),
    val allowedUsernames: List<String> = emptyList(),
    val devMode: Boolean = false,
    val miniAppAutoReply: TelegramMiniAppAutoReplyConfig = TelegramMiniAppAutoReplyConfig(),
)

data class TelegramMiniAppAutoReplyConfig(
    val enabled: Boolean = false,
    /** Юзернейм бота без @, например: my_bot */
    val botUsername: String? = null,
    /** Short name Mini App — последний сегмент в https://t.me/{botUsername}/{appShortName} */
    val appShortName: String? = null,
    val buttonText: String = "Open Mini App",
    val replyText: String = "Got your link. Open Mini App to continue.",
    /** Список regex для фильтрации URL. Если пуст — реагируем на любой URL */
    val urlPatterns: List<String> = emptyList(),
    val pollingTimeoutSeconds: Int = 60,
)
