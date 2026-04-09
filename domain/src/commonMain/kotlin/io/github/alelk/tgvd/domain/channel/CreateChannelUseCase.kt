package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlin.time.Clock

class CreateChannelUseCase(
    private val channelRepository: ChannelRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(request: CreateChannelRequest): Either<DomainError, Channel> =
        txRunner.inRwTransaction {
            either {
                channelRepository.save(request.toChannel(clock.now())).bind()
            }
        }
}

