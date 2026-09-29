package io.github.alelk.tgvd.server

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.domain.system.ReadinessProbe
import io.github.alelk.tgvd.server.config.loadConfig
import io.github.alelk.tgvd.server.config.requireValidConfig
import io.github.alelk.tgvd.server.di.serverModules
import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.CorsConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.github.alelk.tgvd.server.infra.db.DatabaseFactory
import io.github.alelk.tgvd.server.infra.process.YtDlpBootstrap
import io.github.alelk.tgvd.server.infra.service.JobProcessor
import io.github.alelk.tgvd.server.telegram.TelegramMiniAppAutoReplyBot
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthPlugin
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthValidator
import io.github.alelk.tgvd.server.transport.error.configureDomainErrorHandling
import io.github.alelk.tgvd.server.transport.route.channelRoutes
import io.github.alelk.tgvd.server.transport.route.healthRoutes
import io.github.alelk.tgvd.server.transport.route.jobRoutes
import io.github.alelk.tgvd.server.transport.route.previewRoutes
import io.github.alelk.tgvd.server.transport.route.ruleRoutes
import io.github.alelk.tgvd.server.transport.route.systemRoutes
import io.github.alelk.tgvd.server.transport.route.workspaceRoutes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.events.EventDefinition
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import kotlin.system.exitProcess
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/**
 * Start order: config → validation (fail-fast) → [module] (database + migrations, then Koin and HTTP).
 * Any failure before the server listens is logged and ends the process with exit code 1 — a broken
 * config or a failed migration is visible at once instead of a server that answers without a schema.
 */
@Suppress("TooGenericExceptionCaught") // the last line of defence: log whatever stopped the start
fun main() {
    try {
        val config = loadConfig()
        requireValidConfig(config)
        logger.info { "Starting TG Video Downloader server on ${config.server.host}:${config.server.port}" }
        embeddedServer(
            Netty,
            port = config.server.port,
            host = config.server.host,
        ) {
            module(config)
        }.start(wait = true)
    } catch (e: Throwable) {
        logger.error(e) { "Server failed to start" }
        exitProcess(1)
    }
}

/**
 * The whole server as one testable Ktor module. Order:
 * 1. [requireValidConfig] — throws on a config the server must not run with;
 * 2. when [eagerDatabase]: connection pool → Flyway `migrate` → Exposed database, **before** Koin and
 *    routing, so a failed migration throws here and no route is ever installed. `false` boots without
 *    PostgreSQL (the API-surface test): nothing that needs the database is resolvable and
 *    `/health/ready` answers 503;
 * 3. Koin with the opened database, the HTTP plugin stack, routing;
 * 4. when [startBackgroundServices]: the yt-dlp bootstrap + [JobProcessor] and the Telegram Mini App
 *    auto-reply bot, tied to THIS application's lifecycle. Route tests pass `false`.
 *
 * [overrides] are loaded after the server modules with overriding allowed, so tests can replace
 * external adapters (`VideoInfoExtractor`, `VideoDownloader`, `YtDlpService`) with fakes.
 */
fun Application.module(
    config: AppConfig,
    eagerDatabase: Boolean = true,
    startBackgroundServices: Boolean = true,
    overrides: List<Module> = emptyList(),
) {
    requireValidConfig(config)
    logTelegramAccess(config.telegram)

    val openDatabase = if (eagerDatabase) DatabaseFactory(config.db).open() else null
    val lifecycle = LifecycleSubscriptions(this)
    lifecycle.on(ApplicationStopped) {
        openDatabase?.close()
        lifecycle.disposeAll()
    }

    install(Koin) {
        modules(serverModules(config, openDatabase?.database))
        if (overrides.isNotEmpty()) {
            allowOverride(true)
            modules(overrides)
        }
    }

    configureHttp(config.cors)
    configureRouting(config.telegram)

    if (startBackgroundServices) {
        configureJobProcessor(lifecycle)
        configureTelegramMiniAppAutoReplyBot(config, lifecycle)
    }
}

/** Says out loud when access is wider than it looks: dev mode, or an empty allow-list. */
private fun logTelegramAccess(telegram: TelegramConfig) {
    if (telegram.devMode) {
        logger.warn {
            "Telegram dev mode is ON: the header 'X-Telegram-Init-Data: dev' is accepted without a signature. " +
                "Never in production (TELEGRAM_DEV_MODE=false)."
        }
    }
    if (telegram.allowedUserIds.isEmpty() && telegram.allowedUsernames.isEmpty()) {
        logger.warn {
            "Telegram allow-list is empty: access is open to ANY Telegram user. " +
                "Set TELEGRAM_ALLOWED_USER_IDS and/or TELEGRAM_ALLOWED_USERNAMES to restrict it."
        }
    } else {
        logger.info {
            "Telegram allow-list: ${telegram.allowedUserIds.size} user id(s), " +
                "${telegram.allowedUsernames.size} username(s)"
        }
    }
}

/**
 * Plugin stack in the canonical order: ContentNegotiation → DefaultHeaders → CallId → CallLogging →
 * CORS → StatusPages → Resources. The Telegram auth is route-scoped (see [configureRouting]).
 */
@OptIn(ExperimentalUuidApi::class)
private fun Application.configureHttp(corsConfig: CorsConfig) {
    install(ContentNegotiation) {
        json(apiJson)
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
    }

    install(CallId) {
        generate { Uuid.random().toString() }
        replyToHeader("X-Correlation-Id")
    }

    install(CallLogging) {
        callIdMdc("correlationId")
    }

    if (corsConfig.enabled) installCors(corsConfig)

    install(StatusPages) {
        configureDomainErrorHandling()
    }

    install(Resources)
}

private fun Application.installCors(corsConfig: CorsConfig) {
    install(CORS) {
        if (corsConfig.anyHost) {
            anyHost()
        } else {
            corsConfig.hosts.forEach { host ->
                allowHost(host, schemes = listOf("http", "https"))
            }
        }

        allowCredentials = corsConfig.allowCredentials
        allowNonSimpleContentTypes = corsConfig.allowNonSimpleContentTypes

        corsConfig.methods.forEach { m ->
            runCatching { HttpMethod.parse(m) }.getOrNull()?.let { allowMethod(it) }
        }

        corsConfig.headers.forEach { allowHeader(it) }
        corsConfig.exposeHeaders.forEach { exposeHeader(it) }
    }
}

private fun Application.configureRouting(telegramConfig: TelegramConfig) {
    val authValidator = get<TelegramAuthValidator>()
    val readinessProbes = listOf(get<ReadinessProbe>())

    routing {
        // Health probes — no auth required
        healthRoutes(readinessProbes)

        // All API routes — auth required
        route("/") {
            install(TelegramAuthPlugin) {
                validator = authValidator
                allowedUserIds = telegramConfig.allowedUserIds.mapNotNull { it.toLongOrNull() }.toSet()
                allowedUsernames = telegramConfig.allowedUsernames.toSet()
            }

            workspaceRoutes()
            previewRoutes()
            jobRoutes()
            ruleRoutes()
            channelRoutes()
            systemRoutes()
        }
    }
}

private fun Application.configureJobProcessor(lifecycle: LifecycleSubscriptions) {
    val ytDlpBootstrap = get<YtDlpBootstrap>()
    val jobProcessor = get<JobProcessor>()

    lifecycle.on(ApplicationStarted) { application ->
        application.launch {
            ytDlpBootstrap.ensureAvailable()
            jobProcessor.start()
        }
    }
    lifecycle.on(ApplicationStopping) {
        jobProcessor.stop()
    }
}

private fun configureTelegramMiniAppAutoReplyBot(config: AppConfig, lifecycle: LifecycleSubscriptions) {
    val botConfig = config.telegram.miniAppAutoReply
    if (!botConfig.enabled) return

    val bot =
        TelegramMiniAppAutoReplyBot(
            botToken = config.telegram.botToken,
            config = botConfig,
            proxyConfig = config.proxy,
        )

    lifecycle.on(ApplicationStarted) {
        bot.start()
    }
    lifecycle.on(ApplicationStopping) {
        bot.stop()
    }
}

/**
 * Lifecycle subscriptions of ONE application instance.
 *
 * `monitor` belongs to the environment and is shared by every application instance the process
 * builds; on a development reload Ktor starts the new instance before stopping the old one. An
 * unguarded handler of the new instance would receive the old instance's stop events and stop its own
 * services. Ktor 3.4.0 has no `monitor.subscribeFor(application, …)`, so every handler here is guarded
 * by `it === application`, and [disposeAll] (called on this instance's `ApplicationStopped`) removes
 * the handlers so a stopped instance leaves nothing subscribed.
 */
private class LifecycleSubscriptions(private val application: Application) {
    private val handles = mutableListOf<DisposableHandle>()

    fun on(event: EventDefinition<Application>, handler: (Application) -> Unit) {
        handles += application.monitor.subscribe(event) { if (it === application) handler(it) }
    }

    fun disposeAll() {
        handles.forEach { it.dispose() }
        handles.clear()
    }
}
