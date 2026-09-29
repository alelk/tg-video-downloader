package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.WorkspaceId

/**
 * The channel [id] if it belongs to [workspaceId]. A channel of another workspace is reported exactly
 * like a missing one — [DomainError.ChannelNotFound] — so its existence is not revealed.
 */
internal suspend fun ChannelRepository.findInWorkspace(
    id: ChannelDirectoryEntryId,
    workspaceId: WorkspaceId,
): Either<DomainError, Channel> =
    findById(id)?.takeIf { it.workspaceId == workspaceId }?.right() ?: DomainError.ChannelNotFound(id).left()
