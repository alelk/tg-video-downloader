package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.workspace.*
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.workspace.*
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceId
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
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.workspaceRoutes() {
    val workspaceRepository by inject<WorkspaceRepository>()

    // List workspaces for current user
    get<ApiV1.Workspaces> {
        val user = call.telegramUser
        val memberships = workspaceRepository.findByUser(user.id)
        val workspaces = memberships.mapNotNull { member ->
            workspaceRepository.findById(member.workspaceId)?.let { ws ->
                WorkspaceDto(
                    id = ws.id.value.toString(),
                    name = ws.name,
                    role = member.role.name.lowercase(),
                    createdAt = ws.createdAt.toString(),
                )
            }
        }
        call.respond(WorkspaceListResponseDto(items = workspaces))
    }

    // Create a new workspace
    post<ApiV1.Workspaces> {
        val request = call.receive<CreateWorkspaceRequestDto>()
        val user = call.telegramUser
        val now = Clock.System.now()

        val result = either {
            val workspace = Workspace(
                id = WorkspaceId(Uuid.random()),
                name = request.name,
                createdAt = now,
            )
            workspaceRepository.save(workspace).bind()

            // Creator becomes OWNER
            workspaceRepository.addMember(
                WorkspaceMember(
                    workspaceId = workspace.id,
                    userId = user.id,
                    role = WorkspaceRole.OWNER,
                    joinedAt = now,
                )
            ).bind()

            workspace
        }

        call.respondEither<WorkspaceDto, _>(result, HttpStatusCode.Created) { ws ->
            WorkspaceDto(
                id = ws.id.value.toString(),
                name = ws.name,
                role = "owner",
                createdAt = ws.createdAt.toString(),
            )
        }
    }

    // List members of a workspace
    get<ApiV1.Workspaces.ById.Members> { res ->
        val result = either {
            val wsId = parseWorkspaceId(res.parent.workspaceId).bind()
            val user = call.telegramUser
            if (!workspaceRepository.isMember(wsId, user.id)) {
                raise(DomainError.WorkspaceAccessDenied(wsId, user.id))
            }
            workspaceRepository.findMembers(wsId)
        }

        call.respondEither<WorkspaceMemberListResponseDto, _>(result) { members ->
            WorkspaceMemberListResponseDto(
                items = members.map {
                    WorkspaceMemberDto(
                        userId = it.userId.value,
                        role = it.role.name.lowercase(),
                        joinedAt = it.joinedAt.toString(),
                    )
                }
            )
        }
    }

    // Add member to workspace (only OWNER)
    post<ApiV1.Workspaces.ById.Members> { res ->
        val request = call.receive<AddMemberRequestDto>()

        val result = either {
            val wsId = parseWorkspaceId(res.parent.workspaceId).bind()
            val user = call.telegramUser

            // Check caller is owner
            val callerMembership = workspaceRepository.findMembers(wsId)
                .find { it.userId == user.id }
            if (callerMembership == null || callerMembership.role != WorkspaceRole.OWNER) {
                raise(DomainError.WorkspaceAccessDenied(wsId, user.id))
            }

            val role = when (request.role.lowercase()) {
                "owner" -> WorkspaceRole.OWNER
                else -> WorkspaceRole.MEMBER
            }

            val member = WorkspaceMember(
                workspaceId = wsId,
                userId = TelegramUserId(request.userId),
                role = role,
                joinedAt = Clock.System.now(),
            )
            workspaceRepository.addMember(member).bind()
        }

        call.respondEither<WorkspaceMemberDto, _>(result, HttpStatusCode.Created) { member ->
            WorkspaceMemberDto(
                userId = member.userId.value,
                role = member.role.name.lowercase(),
                joinedAt = member.joinedAt.toString(),
            )
        }
    }

    // Remove member from workspace (only OWNER)
    delete<ApiV1.Workspaces.ById.Members.ByUserId> { res ->
        val result = either {
            val wsId = parseWorkspaceId(res.parent.parent.workspaceId).bind()
            val user = call.telegramUser

            val callerMembership = workspaceRepository.findMembers(wsId)
                .find { it.userId == user.id }
            if (callerMembership == null || callerMembership.role != WorkspaceRole.OWNER) {
                raise(DomainError.WorkspaceAccessDenied(wsId, user.id))
            }

            val targetUserId = TelegramUserId(res.userId)
            if (!workspaceRepository.removeMember(wsId, targetUserId)) {
                raise(DomainError.ValidationError("userId", "User ${res.userId} is not a member"))
            }
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}
