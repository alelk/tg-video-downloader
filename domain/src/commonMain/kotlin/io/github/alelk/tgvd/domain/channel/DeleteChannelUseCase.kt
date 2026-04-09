package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.tx.TransactionRunner

class DeleteChannelUseCase(
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(channelId: ChannelDirectoryEntryId): Either<DomainError, Unit> =
        txRunner.inRwTransaction {
            either {
                if (!channelRepository.delete(channelId)) raise(DomainError.ChannelNotFound(channelId))
            }
        }
}

