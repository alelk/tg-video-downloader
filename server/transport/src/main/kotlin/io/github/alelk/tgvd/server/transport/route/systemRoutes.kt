package io.github.alelk.tgvd.server.transport.route

import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.system.*
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.alelk.tgvd.server.transport.error.apiError
import io.github.alelk.tgvd.server.transport.util.correlationId
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.systemRoutes() {
    val ytDlpService by inject<YtDlpService>()
    val settingsHolder by inject<SystemSettingsHolder>()

    // --- Settings ---

    get<ApiV1.System.Settings> {
        val ytDlpConfig = settingsHolder.ytDlpConfig
        val proxyConfig = settingsHolder.proxyConfig
        call.respond(
            SystemSettingsDto(
                ytDlp = YtDlpSettingsDto(
                    cookiesFromBrowser = ytDlpConfig.cookiesFromBrowser,
                    cookiesFile = ytDlpConfig.cookiesFile,
                ),
                proxy = ProxySettingsDto(
                    enabled = proxyConfig.enabled,
                    type = proxyConfig.type.name,
                    host = proxyConfig.host,
                    port = proxyConfig.port,
                    username = proxyConfig.username,
                    password = null, // masked
                ),
            ),
        )
    }

    put<ApiV1.System.Settings> {
        val request = call.receive<SystemSettingsDto>()

        settingsHolder.updateYtDlpConfig { current ->
            current.copy(
                cookiesFromBrowser = request.ytDlp.cookiesFromBrowser,
                cookiesFile = request.ytDlp.cookiesFile,
            )
        }

        settingsHolder.updateProxyConfig { current ->
            current.copy(
                enabled = request.proxy.enabled,
                type = ProxyConfig.ProxyType.entries
                    .find { it.name.equals(request.proxy.type, ignoreCase = true) }
                    ?: current.type,
                host = request.proxy.host,
                port = request.proxy.port,
                username = request.proxy.username,
                password = request.proxy.password ?: current.password,
            )
        }

        call.respond(HttpStatusCode.OK, request.copy(
            proxy = request.proxy.copy(password = null),
        ))
    }

    // --- yt-dlp ---

    get<ApiV1.System.YtDlp.Status> {
        call.respondEither(ytDlpService.version()) { version ->
            YtDlpStatusDto(
                currentVersion = version.version,
                latestVersion = null,
                isUpdateAvailable = false,
            )
        }
    }

    post<ApiV1.System.YtDlp.Update> {
        val ytDlpConfig = settingsHolder.ytDlpConfig
        if (!ytDlpConfig.allowUpdate) {
            call.respond(HttpStatusCode.Forbidden, apiError("UPDATE_DISABLED", "Update is disabled by administrator", call.correlationId))
            return@post
        }

        call.respondEither(ytDlpService.update(), HttpStatusCode.Accepted) { version ->
            YtDlpUpdateResponseDto(
                status = "UPDATED",
                message = "Updated to version ${version.version}",
            )
        }
    }
}

