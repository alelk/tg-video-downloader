package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug

interface WorkspaceRepository {
    suspend fun findById(id: WorkspaceId): Workspace?
    suspend fun findBySlug(slug: WorkspaceSlug): Workspace?
    suspend fun findByUser(userId: TelegramUserId): List<WorkspaceMember>
    suspend fun findMembers(workspaceId: WorkspaceId): List<WorkspaceMember>
    suspend fun isMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean
    suspend fun save(workspace: Workspace): Either<DomainError, Workspace>
    suspend fun addMember(member: WorkspaceMember): Either<DomainError, WorkspaceMember>
    suspend fun removeMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean
}

