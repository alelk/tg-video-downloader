package io.github.alelk.tgvd.server.infra.db.repository
import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceMember
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.mapping.toDbString
import io.github.alelk.tgvd.server.infra.db.mapping.toWorkspaceRole
import io.github.alelk.tgvd.server.infra.db.table.WorkspaceMembersTable
import io.github.alelk.tgvd.server.infra.db.table.WorkspacesTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
private val logger = KotlinLogging.logger {}
@OptIn(ExperimentalUuidApi::class)
class WorkspaceRepositoryImpl(
    private val database: Database,
) : WorkspaceRepository {
    override suspend fun findById(id: WorkspaceId): Workspace? = dbQuery(database) {
        WorkspacesTable.selectAll()
            .where { WorkspacesTable.id eq id.value }
            .singleOrNull()
            ?.toWorkspace()
    }
    override suspend fun findByUser(userId: TelegramUserId): List<WorkspaceMember> = dbQuery(database) {
        WorkspaceMembersTable.selectAll()
            .where { WorkspaceMembersTable.userId eq userId.value }
            .map { it.toWorkspaceMember() }
    }
    override suspend fun findMembers(workspaceId: WorkspaceId): List<WorkspaceMember> = dbQuery(database) {
        WorkspaceMembersTable.selectAll()
            .where { WorkspaceMembersTable.workspaceId eq workspaceId.value }
            .map { it.toWorkspaceMember() }
    }
    override suspend fun isMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean = dbQuery(database) {
        WorkspaceMembersTable.selectAll()
            .where {
                (WorkspaceMembersTable.workspaceId eq workspaceId.value) and
                    (WorkspaceMembersTable.userId eq userId.value)
            }
            .count() > 0
    }
    override suspend fun save(workspace: Workspace): Either<DomainError, Workspace> = dbQuery(database) {
        val exists = WorkspacesTable.selectAll()
            .where { WorkspacesTable.id eq workspace.id.value }
            .count() > 0
        if (exists) {
            WorkspacesTable.update({ WorkspacesTable.id eq workspace.id.value }) {
                it[name] = workspace.name
            }
        } else {
            WorkspacesTable.insert {
                it[id] = workspace.id.value
                it[name] = workspace.name
            }
        }
        workspace.right()
    }
    override suspend fun addMember(member: WorkspaceMember): Either<DomainError, WorkspaceMember> = dbQuery(database) {
        WorkspaceMembersTable.insert {
            it[workspaceId] = member.workspaceId.value
            it[userId] = member.userId.value
            it[role] = member.role.toDbString()
        }
        member.right()
    }
    override suspend fun removeMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean = dbQuery(database) {
        WorkspaceMembersTable.deleteWhere {
            (WorkspaceMembersTable.workspaceId eq workspaceId.value) and
                (WorkspaceMembersTable.userId eq userId.value)
        } > 0
    }
    private fun ResultRow.toWorkspace(): Workspace = Workspace(
        id = WorkspaceId(this[WorkspacesTable.id].value),
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
