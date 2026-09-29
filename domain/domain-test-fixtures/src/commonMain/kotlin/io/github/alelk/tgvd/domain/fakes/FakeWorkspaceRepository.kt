package io.github.alelk.tgvd.domain.fakes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceMember
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository

/**
 * In-memory [WorkspaceRepository] that keeps the port's contract like the PostgreSQL adapter does:
 * a slug belongs to one workspace ([DomainError.WorkspaceSlugConflict] otherwise), membership is
 * per workspace — a member of one workspace is not a member of another.
 */
class FakeWorkspaceRepository : WorkspaceRepository {
    private val workspaces = mutableMapOf<WorkspaceId, Workspace>()
    private val members = mutableListOf<WorkspaceMember>()

    /** Stores [workspace] with [members] (no checks) and returns it. */
    fun seed(workspace: Workspace, vararg members: WorkspaceMember): Workspace {
        workspaces[workspace.id] = workspace
        this.members += members
        return workspace
    }

    override suspend fun findById(id: WorkspaceId): Workspace? = workspaces[id]

    override suspend fun findBySlug(slug: WorkspaceSlug): Workspace? = workspaces.values.find { it.slug == slug }

    override suspend fun findByUser(userId: TelegramUserId): List<WorkspaceMember> =
        members.filter { it.userId == userId }

    override suspend fun findMembers(workspaceId: WorkspaceId): List<WorkspaceMember> =
        members.filter { it.workspaceId == workspaceId }

    override suspend fun isMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean =
        members.any { it.workspaceId == workspaceId && it.userId == userId }

    override suspend fun save(workspace: Workspace): Either<DomainError, Workspace> {
        val slugOwner = findBySlug(workspace.slug)
        if (slugOwner != null && slugOwner.id != workspace.id) {
            return DomainError.WorkspaceSlugConflict(workspace.slug).left()
        }
        workspaces[workspace.id] = workspace
        return workspace.right()
    }

    override suspend fun addMember(member: WorkspaceMember): Either<DomainError, WorkspaceMember> {
        check(workspaces.containsKey(member.workspaceId)) { "No workspace ${member.workspaceId} (foreign key)" }
        check(!isMember(member.workspaceId, member.userId)) { "Duplicate member (primary key)" }
        members += member
        return member.right()
    }

    override suspend fun removeMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean =
        members.removeAll { it.workspaceId == workspaceId && it.userId == userId }
}
