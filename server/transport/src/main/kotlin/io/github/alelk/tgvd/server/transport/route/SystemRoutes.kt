package io.github.alelk.tgvd.server.transport.route

import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.api.contract.system.YtDlpUpdateResponseDto
import io.github.alelk.tgvd.api.mapping.system.toDto
import io.github.alelk.tgvd.api.mapping.system.toUpdateRequest
import io.github.alelk.tgvd.api.mapping.system.toUpdateResponse
import io.github.alelk.tgvd.api.mapping.system.toUpdateResponseDto
import io.github.alelk.tgvd.domain.system.GetSystemSettingsUseCase
import io.github.alelk.tgvd.domain.system.GetYtDlpStatusUseCase
import io.github.alelk.tgvd.domain.system.UpdateSystemSettingsUseCase
import io.github.alelk.tgvd.domain.system.UpdateYtDlpUseCase
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/** `/system/settings` and `/system/yt-dlp/…`. */
fun Route.systemRoutes() {
    val getSettings by inject<GetSystemSettingsUseCase>()
    val updateSettings by inject<UpdateSystemSettingsUseCase>()
    val getYtDlpStatus by inject<GetYtDlpStatusUseCase>()
    val updateYtDlp by inject<UpdateYtDlpUseCase>()

    get<ApiV1.System.Settings> {
        call.respond(getSettings().toDto())
    }

    put<ApiV1.System.Settings> {
        val body = call.receive<SystemSettingsDto>()
        updateSettings(body.toUpdateRequest())
        call.respond(HttpStatusCode.OK, body.toUpdateResponse())
    }

    get<ApiV1.System.YtDlp.Status> {
        call.respondEither<YtDlpStatusDto, _>(getYtDlpStatus()) { it.toDto() }
    }

    post<ApiV1.System.YtDlp.Update> {
        call.respondEither<YtDlpUpdateResponseDto, _>(updateYtDlp(), HttpStatusCode.Accepted) {
            it.toUpdateResponseDto()
        }
    }
}
