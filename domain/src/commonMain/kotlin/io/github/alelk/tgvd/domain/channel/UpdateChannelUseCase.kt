package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class UpdateChannelUseCase(
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(
        channelId: ChannelDirectoryEntryId,
        request: UpdateChannelRequest,
    ): Either<DomainError, Channel> =
        txRunner.inRwTransaction {
            either {
                val existing =
                    channelRepository.findById(channelId)
                        ?: raise(DomainError.ChannelNotFound(channelId))
                val updated =
                    existing.copy(
                        name = request.name ?: existing.name,
                        tags = request.tags ?: existing.tags,
                        metadataOverrides = request.metadataOverrides ?: existing.metadataOverrides,
                        notes = request.notes ?: existing.notes,
                        updatedAt = clock.now(),
                    )
                channelRepository.save(updated).bind()
            }
        }
}

