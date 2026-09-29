package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import kotlin.time.Clock

/**
 * Applies [UpdateChannelRequest] to a channel of the workspace [workspaceSlug].
 * A channel of another workspace is [DomainError.ChannelNotFound].
 */
class UpdateChannelUseCase(
    private val workspaceAccess: WorkspaceAccess,
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        workspaceSlug: WorkspaceSlug,
        actor: TelegramUserId,
        channelId: ChannelDirectoryEntryId,
        request: UpdateChannelRequest,
    ): Either<DomainError, Channel> = txRunner.inRwTransaction {
        either {
            val workspace = workspaceAccess.requireMember(workspaceSlug, actor).bind()
            val existing = channelRepository.findInWorkspace(channelId, workspace.id).bind()
            val updated =
                existing.copy(
                    name = request.name ?: existing.name,
                    tags = request.tags ?: existing.tags,
                    metadataOverrides = request.metadataOverrides ?: existing.metadataOverrides,
                    notes = request.notes ?: existing.notes,
                    trackPreferences =
                    when (val prefs = request.trackPreferences) {
                        null -> existing.trackPreferences
                        else -> prefs.takeUnless { it.isEmpty }
                    },
                    updatedAt = clock.now(),
                )
            channelRepository.save(updated).bind()
        }
    }
}
