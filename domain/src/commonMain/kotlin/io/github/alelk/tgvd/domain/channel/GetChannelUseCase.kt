package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/**
 * Reads one channel of the workspace [workspaceSlug]; a channel of another workspace is
 * [DomainError.ChannelNotFound].
 */
class GetChannelUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        channelId: ChannelDirectoryEntryId,
    ): Either<DomainError, Channel> = txRunner.inRoTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            channelRepository.findInWorkspace(channelId, workspace.id).bind()
        }
    }
}
