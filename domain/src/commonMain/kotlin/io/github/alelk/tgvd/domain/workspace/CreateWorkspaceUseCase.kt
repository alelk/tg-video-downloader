package io.github.alelk.tgvd.domain.workspace

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result of [CreateWorkspaceUseCase]: either a newly created workspace or an existing one
 * the caller was added to as a member.
 */
data class CreateWorkspaceResult(
    val workspace: Workspace,
    val membership: WorkspaceMember,
    val created: Boolean,
)

@OptIn(ExperimentalUuidApi::class)
class CreateWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    /**
     * Creates a new workspace with [slug] and [name], making [createdBy] its OWNER.
     *
     * If a workspace with [slug] already exists the caller is added as MEMBER (if not already a member)
     * and the existing workspace is returned with [CreateWorkspaceResult.created] = false.
     * This idempotent behaviour lets the client call this endpoint safely on reconnect.
     */
    suspend operator fun invoke(
        slug: WorkspaceSlug,
        name: String,
        createdBy: TelegramUserId,
    ): Either<DomainError, CreateWorkspaceResult> =
        txRunner.inRwTransaction {
            either {
                val now = clock.now()
                val existing = workspaceRepository.findBySlug(slug)

                if (existing != null) {
                    val membership =
                        if (!workspaceRepository.isMember(existing.id, createdBy)) {
                            workspaceRepository.addMember(
                                WorkspaceMember(
                                    workspaceId = existing.id,
                                    userId = createdBy,
                                    role = WorkspaceRole.MEMBER,
                                    joinedAt = now,
                                )
                            ).bind()
                        } else {
                            workspaceRepository.findMembers(existing.id).first { it.userId == createdBy }
                        }
                    return@either CreateWorkspaceResult(existing, membership, created = false)
                }

                val workspace =
                    Workspace(
                        id = WorkspaceId(Uuid.random()),
                        slug = slug,
                        name = name,
                        createdAt = now,
                    )
                workspaceRepository.save(workspace).bind()

                val membership =
                    workspaceRepository.addMember(
                        WorkspaceMember(
                            workspaceId = workspace.id,
                            userId = createdBy,
                            role = WorkspaceRole.OWNER,
                            joinedAt = now,
                        )
                    ).bind()

                CreateWorkspaceResult(workspace, membership, created = true)
            }
        }
}

