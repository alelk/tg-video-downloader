@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.workspace.*
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
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
                ws.toDto(member.role.name.lowercase())
            }
        }
        call.respond(WorkspaceListResponseDto(items = workspaces))
    }

    // Create a new workspace (or return existing if slug already taken)
    post<ApiV1.Workspaces> {
        val request = call.receive<CreateWorkspaceRequestDto>()
        val user = call.telegramUser
        val now = Clock.System.now()

        val result = either {
            val slug = runCatching { WorkspaceSlug(request.slug) }
                .getOrElse { raise(DomainError.ValidationError("slug", it.message ?: "Invalid slug")) }

            // Check if workspace with this slug already exists
            val existing = workspaceRepository.findBySlug(slug)
            if (existing != null) {
                // Ensure the caller is a member; if not, add as MEMBER
                if (!workspaceRepository.isMember(existing.id, user.id)) {
                    workspaceRepository.addMember(
                        WorkspaceMember(
                            workspaceId = existing.id,
                            userId = user.id,
                            role = WorkspaceRole.MEMBER,
                            joinedAt = now,
                        )
                    ).bind()
                }
                val membership = workspaceRepository.findMembers(existing.id)
                    .find { it.userId == user.id }
                return@post call.respond(HttpStatusCode.OK, existing.toDto(membership?.role?.name?.lowercase() ?: "member"))
            }

            val workspace = Workspace(
                id = WorkspaceId(Uuid.random()),
                slug = slug,
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
            ws.toDto("owner")
        }
    }

    // List members of a workspace
    get<ApiV1.Workspaces.ById.Members> { res ->
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug)
                ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val user = call.telegramUser
            if (!workspaceRepository.isMember(ws.id, user.id)) {
                raise(DomainError.WorkspaceAccessDenied(ws.id, user.id))
            }
            workspaceRepository.findMembers(ws.id)
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
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug)
                ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val user = call.telegramUser

            // Check caller is owner
            val callerMembership = workspaceRepository.findMembers(ws.id)
                .find { it.userId == user.id }
            if (callerMembership == null || callerMembership.role != WorkspaceRole.OWNER) {
                raise(DomainError.WorkspaceAccessDenied(ws.id, user.id))
            }

            val role = when (request.role.lowercase()) {
                "owner" -> WorkspaceRole.OWNER
                else -> WorkspaceRole.MEMBER
            }

            val member = WorkspaceMember(
                workspaceId = ws.id,
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
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug)
                ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val user = call.telegramUser

            val callerMembership = workspaceRepository.findMembers(ws.id)
                .find { it.userId == user.id }
            if (callerMembership == null || callerMembership.role != WorkspaceRole.OWNER) {
                raise(DomainError.WorkspaceAccessDenied(ws.id, user.id))
            }

            val targetUserId = TelegramUserId(res.userId)
            if (!workspaceRepository.removeMember(ws.id, targetUserId)) {
                raise(DomainError.ValidationError("userId", "User ${res.userId} is not a member"))
            }
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun Workspace.toDto(role: String) = WorkspaceDto(
    id = id.value.toString(),
    slug = slug.value,
    name = name,
    role = role,
    createdAt = createdAt.toString(),
)

