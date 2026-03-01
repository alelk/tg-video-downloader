package io.github.alelk.tgvd.server

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.server.di.serverModules
import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthPlugin
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthValidator
import io.github.alelk.tgvd.server.transport.error.configureDomainErrorHandling
import io.github.alelk.tgvd.server.transport.route.jobRoutes
import io.github.alelk.tgvd.server.transport.route.previewRoutes
import io.github.alelk.tgvd.server.transport.route.ruleRoutes
import io.github.alelk.tgvd.server.transport.route.systemRoutes
import io.github.alelk.tgvd.server.transport.route.workspaceRoutes
import io.github.alelk.tgvd.server.infra.service.JobProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        configureApplication(config)
    }.start(wait = true)
}

@OptIn(ExperimentalUuidApi::class)
fun Application.configureApplication(config: AppConfig) {
    install(Koin) {
        modules(serverModules(config))
    }

    install(ContentNegotiation) {
        json(apiJson)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Telegram-Init-Data")
        allowHeader("X-Workspace-Id")
        exposeHeader("X-Correlation-Id")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
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

    configureRouting()
    configureJobProcessor()
}

private fun Application.configureJobProcessor() {
    val jobProcessor by inject<JobProcessor>()

    monitor.subscribe(ApplicationStarted) {
        jobProcessor.start()
    }
    monitor.subscribe(ApplicationStopping) {
        jobProcessor.stop()
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
            }

            workspaceRoutes()
            previewRoutes()
            jobRoutes()
            ruleRoutes()
            systemRoutes()
        }
    }
}

private fun loadConfig(): AppConfig {
    val profile = System.getenv("APP_PROFILE") ?: "local"
    logger.info { "Loading configuration with profile: $profile" }

    return ConfigLoaderBuilder.default()
        .addResourceSource("/application.yaml")
        .addResourceSource("/application-$profile.yaml", optional = true)
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow<AppConfig>()
}
