package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import kotlin.time.Clock

/** Registers a channel in the directory of the workspace [workspaceSlug]; the caller must be a member. */
class CreateChannelUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        request: CreateChannelRequest,
    ): Either<DomainError, Channel> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            channelRepository.save(request.toChannel(workspace.id, clock.now())).bind()
        }
    }
}
