package io.github.alelk.tgvd.api.mapping.workspace

import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceMemberDto
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceMember
import kotlin.uuid.ExperimentalUuidApi

/**
 * Maps a [Workspace] together with the requesting user's [WorkspaceMember] to a [WorkspaceDto].
 *
 * [WorkspaceDto.role] is not a field of [Workspace] itself — it represents the current caller's
 * role and therefore requires the membership context.
 */
@OptIn(ExperimentalUuidApi::class)
fun Workspace.toDto(membership: WorkspaceMember): WorkspaceDto = WorkspaceDto(
    id = id.value.toString(),
    slug = slug.value,
    name = name,
    role = membership.role.name.lowercase(),
    createdAt = createdAt.toString(),
)

/** Convenience overload when the role string is already resolved (e.g. from a list join). */
@OptIn(ExperimentalUuidApi::class)
fun Workspace.toDto(role: String): WorkspaceDto = WorkspaceDto(
    id = id.value.toString(),
    slug = slug.value,
    name = name,
    role = role,
    createdAt = createdAt.toString(),
)

fun WorkspaceMember.toDto(): WorkspaceMemberDto = WorkspaceMemberDto(
    userId = userId.value,
    role = role.name.lowercase(),
    joinedAt = joinedAt.toString(),
)

