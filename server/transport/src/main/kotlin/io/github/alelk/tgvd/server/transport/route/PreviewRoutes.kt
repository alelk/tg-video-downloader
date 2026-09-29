package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.preview.toDomain
import io.github.alelk.tgvd.api.mapping.preview.toDto
import io.github.alelk.tgvd.domain.preview.PreviewVideoUseCase
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.previewRoutes() {
    val previewVideo by inject<PreviewVideoUseCase>()

    post<ApiV1.Workspaces.ById.Preview> { res ->
        val request = call.receive<PreviewRequestDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                previewVideo(
                    workspaceSlug = slug,
                    actor = call.telegramUser.id,
                    url = request.url,
                    overrides = request.overrides?.toDomain(),
                    force = request.force,
                ).bind()
            }
        call.respondEither<PreviewResponseDto, _>(result) { it.toDto() }
    }
}
