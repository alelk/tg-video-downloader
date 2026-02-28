package io.github.alelk.tgvd.server.transport.route

import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.api.contract.system.YtDlpUpdateResponseDto
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.transport.error.apiError
import io.github.alelk.tgvd.server.transport.util.correlationId
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.systemRoutes() {
    val ytDlpService by inject<YtDlpService>()
    val ytDlpConfig by inject<YtDlpConfig>()

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

