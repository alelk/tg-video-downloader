package io.github.alelk.tgvd.domain.channel

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.WorkspaceId

interface ChannelRepository {
    suspend fun findById(id: ChannelDirectoryEntryId): Channel?
    suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Channel>
    suspend fun findByChannelId(workspaceId: WorkspaceId, channelId: ChannelId, extractor: Extractor): Channel?
    suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag): List<Channel>
    suspend fun findByTags(workspaceId: WorkspaceId, tags: Set<Tag>, matchAll: Boolean = false): List<Channel>
    suspend fun save(channel: Channel): Either<DomainError, Channel>
    suspend fun delete(id: ChannelDirectoryEntryId): Boolean
    suspend fun findAllTags(workspaceId: WorkspaceId): Set<Tag>
}

