@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.workspace.*
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.api.mapping.workspace.toDto
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.workspace.*
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.delete
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.workspaceRoutes() {
    val createWorkspace by inject<CreateWorkspaceUseCase>()
    val addWorkspaceMember by inject<AddWorkspaceMemberUseCase>()
    val removeWorkspaceMember by inject<RemoveWorkspaceMemberUseCase>()
    val workspaceRepository by inject<WorkspaceRepository>()

    // List workspaces for the current user
    get<ApiV1.Workspaces> {
        val user = call.telegramUser
        val memberships = workspaceRepository.findByUser(user.id)
        val workspaces =
            memberships.mapNotNull { member ->
                workspaceRepository.findById(member.workspaceId)?.toDto(member)
            }
        call.respond(WorkspaceListResponseDto(items = workspaces))
    }

    // Create a new workspace (idempotent: returns existing if slug already taken)
    post<ApiV1.Workspaces> {
        val request = call.receive<CreateWorkspaceRequestDto>()
        val user = call.telegramUser

        val slug = runCatching { WorkspaceSlug(request.slug) }.getOrElse {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (it.message ?: "Invalid slug"))
            )
            return@post
        }

        val result = createWorkspace(slug, request.name, user.id)
        result.fold(
            ifLeft = { call.respondEither<WorkspaceDto, _>(result) { it.workspace.toDto(it.membership) } },
            ifRight = { createResult ->
                val status = if (createResult.created) HttpStatusCode.Created else HttpStatusCode.OK
                call.respond(status, createResult.workspace.toDto(createResult.membership))
            }
        )
    }

    // List members of a workspace
    get<ApiV1.Workspaces.ById.Members> { res ->
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val user = call.telegramUser
            if (!workspaceRepository.isMember(ws.id, user.id)) raise(DomainError.WorkspaceAccessDenied(ws.id, user.id))
            workspaceRepository.findMembers(ws.id)
        }
        call.respondEither<WorkspaceMemberListResponseDto, _>(result) { members ->
            WorkspaceMemberListResponseDto(items = members.map { it.toDto() })
        }
    }

    // Add member to workspace (OWNER only)
    post<ApiV1.Workspaces.ById.Members> { res ->
        val request = call.receive<AddMemberRequestDto>()
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val role =
                when (request.role.lowercase()) {
                    "owner" -> WorkspaceRole.OWNER
                    else -> WorkspaceRole.MEMBER
                }
            addWorkspaceMember(ws.id, call.telegramUser.id, TelegramUserId(request.userId), role).bind()
        }
        call.respondEither<WorkspaceMemberDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    // Remove member from workspace (OWNER only)
    delete<ApiV1.Workspaces.ById.Members.ByUserId> { res ->
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            removeWorkspaceMember(ws.id, call.telegramUser.id, TelegramUserId(res.userId)).bind()
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}
