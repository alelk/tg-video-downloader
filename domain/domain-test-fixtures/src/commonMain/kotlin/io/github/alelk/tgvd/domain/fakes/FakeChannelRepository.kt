package io.github.alelk.tgvd.domain.fakes

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId

/** In-memory [ChannelRepository]; every lookup is scoped to the workspace, like the PostgreSQL adapter. */
class FakeChannelRepository : ChannelRepository {
    private val channels = linkedMapOf<ChannelDirectoryEntryId, Channel>()

    /** Stores [channel] as is and returns it. */
    fun seed(channel: Channel): Channel = channel.also { channels[it.id] = it }

    override suspend fun findById(id: ChannelDirectoryEntryId): Channel? = channels[id]

    override suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Channel> =
        channels.values.filter { it.workspaceId == workspaceId }.sortedBy { it.name }

    override suspend fun findByChannelId(
        workspaceId: WorkspaceId,
        channelId: ChannelId,
        extractor: Extractor,
    ): Channel? =
        channels.values.find { it.workspaceId == workspaceId && it.channelId == channelId && it.extractor == extractor }

    override suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag): List<Channel> =
        channels.values.filter { it.workspaceId == workspaceId && tag in it.tags }

    override suspend fun findByTags(workspaceId: WorkspaceId, tags: Set<Tag>, matchAll: Boolean): List<Channel> {
        if (tags.isEmpty()) return emptyList()
        return channels.values.filter { channel ->
            channel.workspaceId == workspaceId &&
                if (matchAll) channel.tags.containsAll(tags) else channel.tags.any { it in tags }
        }
    }

    override suspend fun save(channel: Channel): Either<DomainError, Channel> {
        channels[channel.id] = channel
        return channel.right()
    }

    override suspend fun delete(id: ChannelDirectoryEntryId): Boolean = channels.remove(id) != null

    override suspend fun findAllTags(workspaceId: WorkspaceId): Set<Tag> =
        channels.values.filter { it.workspaceId == workspaceId }.flatMap { it.tags }.toSet()
}
