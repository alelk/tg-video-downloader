package io.github.alelk.tgvd.server.transport.route

import arrow.core.getOrElse
import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.workspace.AddMemberRequestDto
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceListResponseDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceMemberDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceMemberListResponseDto
import io.github.alelk.tgvd.api.mapping.workspace.parseNewWorkspaceSlug
import io.github.alelk.tgvd.api.mapping.workspace.parseTelegramUserId
import io.github.alelk.tgvd.api.mapping.workspace.parseWorkspaceRole
import io.github.alelk.tgvd.api.mapping.workspace.toDto
import io.github.alelk.tgvd.domain.workspace.AddWorkspaceMemberUseCase
import io.github.alelk.tgvd.domain.workspace.CreateWorkspaceUseCase
import io.github.alelk.tgvd.domain.workspace.ListWorkspaceMembersUseCase
import io.github.alelk.tgvd.domain.workspace.ListWorkspacesUseCase
import io.github.alelk.tgvd.domain.workspace.RemoveWorkspaceMemberUseCase
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.workspaceRoutes() {
    workspaceCollectionRoutes()
    workspaceMemberRoutes()
}

/** `GET /workspaces` (the caller's) and `POST /workspaces` (create, or join an existing slug). */
private fun Route.workspaceCollectionRoutes() {
    val listWorkspaces by inject<ListWorkspacesUseCase>()
    val createWorkspace by inject<CreateWorkspaceUseCase>()

    get<ApiV1.Workspaces> {
        val workspaces = listWorkspaces(call.telegramUser.id)
        call.respond(WorkspaceListResponseDto(items = workspaces.map { it.workspace.toDto(it.membership) }))
    }

    // 201 for a new workspace, 200 when the slug is taken and the caller joined it (known risk, kept as is).
    post<ApiV1.Workspaces> {
        val body = call.receive<CreateWorkspaceRequestDto>()
        val slug =
            parseNewWorkspaceSlug(body.slug).getOrElse { error ->
                // Legacy error body `{"error": …}` (not ApiErrorDto) — unchanged for wire compatibility.
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                return@post
            }
        val result = createWorkspace(slug, body.name, call.telegramUser.id)
        val status = if (result.getOrNull()?.created == true) HttpStatusCode.Created else HttpStatusCode.OK
        call.respondEither<WorkspaceDto, _>(result, status) { it.workspace.toDto(it.membership) }
    }
}

/** `GET …/members` (members only), `POST …/members` and `DELETE …/members/{userId}` (OWNER only). */
private fun Route.workspaceMemberRoutes() {
    val listMembers by inject<ListWorkspaceMembersUseCase>()
    val addMember by inject<AddWorkspaceMemberUseCase>()
    val removeMember by inject<RemoveWorkspaceMemberUseCase>()

    get<ApiV1.Workspaces.ById.Members> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                listMembers(slug, call.telegramUser.id).bind()
            }
        call.respondEither<WorkspaceMemberListResponseDto, _>(result) { members ->
            WorkspaceMemberListResponseDto(items = members.map { it.toDto() })
        }
    }

    post<ApiV1.Workspaces.ById.Members> { res ->
        val body = call.receive<AddMemberRequestDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                val userId = parseTelegramUserId(body.userId).bind()
                addMember(slug, call.telegramUser.id, userId, parseWorkspaceRole(body.role)).bind()
            }
        call.respondEither<WorkspaceMemberDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    delete<ApiV1.Workspaces.ById.Members.ByUserId> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val userId = parseTelegramUserId(res.userId).bind()
                removeMember(slug, call.telegramUser.id, userId).bind()
            }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}
