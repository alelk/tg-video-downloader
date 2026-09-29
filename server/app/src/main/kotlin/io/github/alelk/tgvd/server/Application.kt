package io.github.alelk.tgvd.server

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.server.di.serverModules
import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.CorsConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.github.alelk.tgvd.server.infra.process.YtDlpBootstrap
import io.github.alelk.tgvd.server.infra.service.JobProcessor
import io.github.alelk.tgvd.server.telegram.TelegramMiniAppAutoReplyBot
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthPlugin
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthValidator
import io.github.alelk.tgvd.server.transport.error.configureDomainErrorHandling
import io.github.alelk.tgvd.server.transport.route.channelRoutes
import io.github.alelk.tgvd.server.transport.route.jobRoutes
import io.github.alelk.tgvd.server.transport.route.previewRoutes
import io.github.alelk.tgvd.server.transport.route.ruleRoutes
import io.github.alelk.tgvd.server.transport.route.systemRoutes
import io.github.alelk.tgvd.server.transport.route.workspaceRoutes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

fun main() {
    val config = loadConfig()
    logger.info { "Starting TG Video Downloader server on ${config.server.host}:${config.server.port}" }

    embeddedServer(
        Netty,
        port = config.server.port,
        host = config.server.host,
    ) {
        module(config)
    }.start(wait = true)
}

/**
 * The whole server as one testable Ktor module.
 *
 * - [eagerDatabase] resolves the [Database] (connection pool + Flyway migrations) right after Koin is
 *   installed instead of on the first request that needs it. The API-surface test passes `false` to
 *   boot without PostgreSQL.
 * - [startBackgroundServices] controls the yt-dlp bootstrap, the [JobProcessor] and the Telegram
 *   Mini App auto-reply bot. Route tests pass `false`.
 * - [overrides] are loaded after the server modules with overriding allowed, so tests can replace
 *   external adapters (`VideoInfoExtractor`, `VideoDownloader`, `YtDlpService`) with fakes.
 */
fun Application.module(
    config: AppConfig,
    eagerDatabase: Boolean = true,
    startBackgroundServices: Boolean = true,
    overrides: List<Module> = emptyList(),
) {
    install(Koin) {
        modules(serverModules(config))
        if (overrides.isNotEmpty()) {
            allowOverride(true)
            modules(overrides)
        }
    }
    if (eagerDatabase) get<Database>()

    configureHttp(config.cors)
    configureRouting()

    if (startBackgroundServices) {
        configureJobProcessor()
        configureTelegramMiniAppAutoReplyBot(config)
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun Application.configureHttp(corsConfig: CorsConfig) {
    install(ContentNegotiation) {
        json(apiJson)
    }

    if (corsConfig.enabled) {
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

    install(Resources)

    install(StatusPages) {
        configureDomainErrorHandling()
    }
}

private fun Application.configureJobProcessor() {
    val ytDlpBootstrap by inject<YtDlpBootstrap>()
    val jobProcessor by inject<JobProcessor>()

    monitor.subscribe(ApplicationStarted) { application ->
        application.launch {
            ytDlpBootstrap.ensureAvailable()
            jobProcessor.start()
        }
    }
    monitor.subscribe(ApplicationStopping) {
        jobProcessor.stop()
    }
}

private fun Application.configureTelegramMiniAppAutoReplyBot(config: AppConfig) {
    val botConfig = config.telegram.miniAppAutoReply
    if (!botConfig.enabled) return

    val bot = TelegramMiniAppAutoReplyBot(
        botToken = config.telegram.botToken,
        config = botConfig,
        proxyConfig = config.proxy,
    )

    monitor.subscribe(ApplicationStarted) {
        bot.start()
    }
    monitor.subscribe(ApplicationStopping) {
        bot.stop()
    }
}

private fun Application.configureRouting() {
    val authValidator by inject<TelegramAuthValidator>()
    val telegramConfig by inject<TelegramConfig>()

    routing {
        // Health check — no auth required
        get("/health") {
            call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
        }

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

private fun loadConfig(): AppConfig {
    val profile = System.getenv("APP_PROFILE") ?: "local"
    val externalConfig = System.getenv("APP_CONFIG") ?: "/app/config/application.yaml"
    logger.info { "Loading configuration with profile: $profile, external config: $externalConfig" }

    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addFileSource(externalConfig, optional = true) // 1. External file (highest priority)
        .addResourceSource("/application-$profile.yaml", optional = true) // 2. Profile-specific
        .addResourceSource("/application.yaml") // 3. Defaults
        .build()
        .loadConfigOrThrow<AppConfig>()
}
