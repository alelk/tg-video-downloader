package io.github.alelk.tgvd.server.infra.db.repository

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
import io.github.alelk.tgvd.server.infra.db.catchingDb
import io.github.alelk.tgvd.server.infra.db.mapping.toDbString
import io.github.alelk.tgvd.server.infra.db.mapping.toWorkspaceRole
import io.github.alelk.tgvd.server.infra.db.table.WorkspaceMembersTable
import io.github.alelk.tgvd.server.infra.db.table.WorkspacesTable
import io.github.alelk.tgvd.server.infra.db.violatesUnique
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi

/** Unique index on `workspaces.slug` (V1). */
private const val SLUG_UNIQUE_INDEX = "idx_workspaces_slug"

/** Runs in the transaction of the caller (`TransactionRunner`); never opens one. */
@OptIn(ExperimentalUuidApi::class)
class WorkspaceRepositoryImpl : WorkspaceRepository {
    override suspend fun findById(id: WorkspaceId): Workspace? = WorkspacesTable.selectAll()
        .where { WorkspacesTable.id eq id.value }
        .singleOrNull()
        ?.toWorkspace()

    override suspend fun findBySlug(slug: WorkspaceSlug): Workspace? = WorkspacesTable.selectAll()
        .where { WorkspacesTable.slug eq slug.value }
        .singleOrNull()
        ?.toWorkspace()

    override suspend fun findByUser(userId: TelegramUserId): List<WorkspaceMember> = WorkspaceMembersTable.selectAll()
        .where { WorkspaceMembersTable.userId eq userId.value }
        .map { it.toWorkspaceMember() }

    override suspend fun findMembers(workspaceId: WorkspaceId): List<WorkspaceMember> =
        WorkspaceMembersTable.selectAll()
            .where { WorkspaceMembersTable.workspaceId eq workspaceId.value }
            .map { it.toWorkspaceMember() }

    override suspend fun isMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean =
        WorkspaceMembersTable.selectAll()
            .where {
                (WorkspaceMembersTable.workspaceId eq workspaceId.value) and
                    (WorkspaceMembersTable.userId eq userId.value)
            }
            .count() > 0

    /**
     * Inserts or updates [workspace]. A slug taken by another workspace is
     * [DomainError.WorkspaceSlugConflict] — found by the check before the write, or, when a concurrent
     * transaction took the slug in between, by the unique index.
     */
    override suspend fun save(workspace: Workspace): Either<DomainError, Workspace> = catchingDb(
        onUniqueViolation = { e ->
            if (e.violatesUnique(SLUG_UNIQUE_INDEX)) DomainError.WorkspaceSlugConflict(workspace.slug) else null
        },
    ) {
        val slugOwner = WorkspacesTable.selectAll()
            .where { WorkspacesTable.slug eq workspace.slug.value }
            .singleOrNull()
            ?.get(WorkspacesTable.id)
            ?.value
        val existsById = WorkspacesTable.selectAll()
            .where { WorkspacesTable.id eq workspace.id.value }
            .count() > 0
        when {
            slugOwner != null && slugOwner != workspace.id.value ->
                DomainError.WorkspaceSlugConflict(workspace.slug).left()

            existsById -> {
                WorkspacesTable.update({ WorkspacesTable.id eq workspace.id.value }) {
                    it[slug] = workspace.slug.value
                    it[name] = workspace.name
                }
                workspace.right()
            }

            else -> {
                WorkspacesTable.insert {
                    it[id] = workspace.id.value
                    it[slug] = workspace.slug.value
                    it[name] = workspace.name
                    it[createdAt] = workspace.createdAt
                }
                workspace.right()
            }
        }
    }

    override suspend fun addMember(member: WorkspaceMember): Either<DomainError, WorkspaceMember> = catchingDb {
        WorkspaceMembersTable.insert {
            it[workspaceId] = member.workspaceId.value
            it[userId] = member.userId.value
            it[role] = member.role.toDbString()
            it[joinedAt] = member.joinedAt
        }
        member.right()
    }

    override suspend fun removeMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean =
        WorkspaceMembersTable.deleteWhere {
            (WorkspaceMembersTable.workspaceId eq workspaceId.value) and
                (WorkspaceMembersTable.userId eq userId.value)
        } > 0

    private fun ResultRow.toWorkspace(): Workspace = Workspace(
        id = WorkspaceId(this[WorkspacesTable.id].value),
        slug = WorkspaceSlug(this[WorkspacesTable.slug]),
        name = this[WorkspacesTable.name],
        createdAt = this[WorkspacesTable.createdAt],
    )

    private fun ResultRow.toWorkspaceMember(): WorkspaceMember = WorkspaceMember(
        workspaceId = WorkspaceId(this[WorkspaceMembersTable.workspaceId].value),
        userId = TelegramUserId(this[WorkspaceMembersTable.userId]),
        role = this[WorkspaceMembersTable.role].toWorkspaceRole(),
        joinedAt = this[WorkspaceMembersTable.joinedAt],
    )
}
