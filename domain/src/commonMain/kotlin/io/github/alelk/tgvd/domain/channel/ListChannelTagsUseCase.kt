package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/** Every tag used by the channels of the workspace [workspaceSlug], once each, sorted by value. */
class ListChannelTagsUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(workspaceSlug: WorkspaceSlug, actor: TelegramUserId): Either<DomainError, List<Tag>> =
        txRunner.inRoTransaction {
            either {
                val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
                channelRepository.findAllTags(workspace.id).sortedBy { it.value }
            }
        }
}
