package io.github.alelk.tgvd.server.telegram

import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.bot.settings.limiters.RequestLimiter
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.URLInlineKeyboardButton
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.utils.matrix
import dev.inmo.tgbotapi.utils.row
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.TelegramMiniAppAutoReplyConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.buildUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64

private val logger = KotlinLogging.logger {}

private fun ProxyConfig.toKtorProxyConfig() =
    takeIf { enabled }?.let { cfg ->
        when (cfg.type) {
            ProxyConfig.ProxyType.HTTP ->
                ProxyBuilder.http(
                    buildUrl {
                        protocol = URLProtocol.HTTP
                        host = cfg.host
                        port = cfg.port
                    }
                )

            ProxyConfig.ProxyType.SOCKS5 -> ProxyBuilder.socks(cfg.host, cfg.port)
        }
    }

/**
 * Long-polling bot на базе dev.inmo:tgbotapi:
 * - читает входящие текстовые сообщения (личка + группы)
 * - извлекает URL видео-сервиса
 * - отвечает inline URL-кнопкой, открывающей Mini App с предзаполненным URL
 */
class TelegramMiniAppAutoReplyBot(
    private val botToken: String,
    private val config: TelegramMiniAppAutoReplyConfig,
    private val proxyConfig: ProxyConfig? = null
) {
    // Собственный изолированный scope — не связан с application scope
    private val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        val botUsername = config.botUsername?.trim()?.trimStart('@').orEmpty()
        val appShortName = config.appShortName?.trim().orEmpty()

        if (botUsername.isBlank() || appShortName.isBlank()) {
            logger.warn { "Telegram Mini App auto-reply bot: botUsername or appShortName is missing — bot will not start" }
            return
        }

        // Компилируем regex-паттерны один раз при старте
        val urlPatterns = config.urlPatterns.mapNotNull { pattern ->
            runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
                .onFailure { logger.warn { "Invalid URL pattern '$pattern': ${it.message}" } }
                .getOrNull()
        }

        logger.info {
            "Telegram Mini App auto-reply bot starting " +
                    "(bot=@$botUsername, app=$appShortName, urlPatterns=${config.urlPatterns})"
        }

        val bot = telegramBot(botToken) {
            client = HttpClient(CIO) {
                if (proxyConfig != null && proxyConfig.enabled) {
                    engine {
                        proxy = proxyConfig.toKtorProxyConfig()
                    }
                    if (proxyConfig.username != null && proxyConfig.password != null) {
                        val credentials =
                            Base64.encode("${proxyConfig.username}:${proxyConfig.password}".toByteArray())
                        defaultRequest {
                            header(HttpHeaders.ProxyAuthorization, "Basic $credentials")
                        }
                    }
                }
            }
        }

        // Запускаем в botScope без блокировки — polling живёт сам по себе
        botScope.launch {
            bot.buildBehaviourWithLongPolling(
                scope = botScope,
                timeoutSeconds = config.pollingTimeoutSeconds,
                defaultExceptionsHandler = { e ->
                    logger.warn(e) { "Telegram Mini App auto-reply bot: handler error" }
                }
            ) {
                logger.info { "Telegram Mini App auto-reply bot connected, waiting for messages..." }

                onContentMessage { message ->
                    val chat = message.chat
                    val content = message.content
                    logger.debug { "Received message: chat=${chat.id.chatId} chatType=${chat::class.simpleName} contentType=${content::class.simpleName}" }

                    if (content !is TextContent) return@onContentMessage
                    val text = content.text

                    val url = MiniAppDeepLink.extractFirstUrl(text)
                    if (url == null) {
                        logger.debug { "No URL found in message from chat=${chat.id.chatId}" }
                        return@onContentMessage
                    }
                    if (!MiniAppDeepLink.matchesUrlPatterns(url, urlPatterns)) {
                        logger.debug { "URL '$url' does not match any pattern, skipping" }
                        return@onContentMessage
                    }

                    val launchUrl = MiniAppDeepLink.buildLaunchUrl(botUsername, appShortName, url)
                    logger.info { "Replying to chat=${chat.id.chatId} (${chat::class.simpleName}) with Mini App button for url=$url" }

                    bot.sendMessage(
                        chatId = chat.id,
                        text = config.replyText,
                        replyMarkup = InlineKeyboardMarkup(
                            keyboard = matrix {
                                row {
                                    +URLInlineKeyboardButton(
                                        text = config.buttonText,
                                        url = launchUrl,
                                    )
                                }
                            }
                        )
                    )
                }
            }.join()
        }
    }

    fun stop() {
        logger.info { "Stopping Telegram Mini App auto-reply bot..." }
        botScope.cancel()
    }
}

