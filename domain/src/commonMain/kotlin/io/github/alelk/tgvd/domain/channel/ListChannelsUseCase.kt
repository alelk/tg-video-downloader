package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess

/** Which channels of the workspace [ListChannelsUseCase] returns. */
sealed interface ChannelFilter {
    /** Every channel of the workspace. */
    data object All : ChannelFilter

    /** The one channel with this platform id on this extractor (none or one). */
    data class ByPlatformId(val channelId: ChannelId, val extractor: Extractor) : ChannelFilter

    /** The channels tagged with [tag]. */
    data class ByTag(val tag: Tag) : ChannelFilter
}

/** Lists the channels of the workspace [workspaceSlug] that match [ChannelFilter]. */
class ListChannelsUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        filter: ChannelFilter,
    ): Either<DomainError, List<Channel>> = txRunner.inRoTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            when (filter) {
                ChannelFilter.All -> channelRepository.findByWorkspace(workspace.id)
                is ChannelFilter.ByPlatformId ->
                    listOfNotNull(channelRepository.findByChannelId(workspace.id, filter.channelId, filter.extractor))
                is ChannelFilter.ByTag -> channelRepository.findByTag(workspace.id, filter.tag)
            }
        }
    }
}
