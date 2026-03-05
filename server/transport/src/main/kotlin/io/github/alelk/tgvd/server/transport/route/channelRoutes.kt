package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.channel.ChannelListResponseDto
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.TagListResponseDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.channel.toDto
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.util.parseId
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.delete
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.channelRoutes() {
    val channelRepository by inject<ChannelRepository>()
    val workspaceRepository by inject<WorkspaceRepository>()

    // GET /api/v1/workspaces/{slug}/channels?tag=...&channelId=...&extractor=...
    get<ApiV1.Workspaces.ById.Channels> { res ->
        val result = either<DomainError, ChannelListResponseDto> {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val channels = when {
                res.channelId != null && res.extractor != null -> {
                    val ch = channelRepository.findByChannelId(ws.id, ChannelId(res.channelId!!), Extractor(res.extractor!!))
                    listOfNotNull(ch)
                }
                res.tag != null -> channelRepository.findByTag(ws.id, Tag(res.tag!!))
                else -> channelRepository.findByWorkspace(ws.id)
            }
            ChannelListResponseDto(items = channels.map { it.toDto() })
        }
        call.respondEither(result)
    }

    // GET /api/v1/workspaces/{slug}/channels/tags
    get<ApiV1.Workspaces.ById.Channels.Tags> { res ->
        val result = either<DomainError, TagListResponseDto> {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val tags = channelRepository.findAllTags(ws.id)
            TagListResponseDto(tags = tags.map { it.value }.sorted())
        }
        call.respondEither(result)
    }

    // POST /api/v1/workspaces/{slug}/channels
    post<ApiV1.Workspaces.ById.Channels> { res ->
        val request = call.receive<CreateChannelDto>()

        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val now = Clock.System.now()
            val channel = Channel(
                id = ChannelDirectoryEntryId(Uuid.random()),
                workspaceId = ws.id,
                channelId = ChannelId(request.channelId),
                extractor = Extractor(request.extractor),
                name = request.name,
                tags = request.tags.map { Tag(it) }.toSet(),
                metadataOverrides = request.metadataOverrides?.toDomain(),
                notes = request.notes,
                createdAt = now,
                updatedAt = now,
            )
            channelRepository.save(channel).bind()
        }

        call.respondEither<ChannelDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    // GET /api/v1/workspaces/{slug}/channels/{id}
    get<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val result = either<DomainError, Channel> {
            val channelId = parseId(res.id, "channelId", ::ChannelDirectoryEntryId).bind()
            channelRepository.findById(channelId) ?: raise(DomainError.ChannelNotFound(channelId))
        }
        call.respondEither<ChannelDto, _>(result) { it.toDto() }
    }

    // PUT /api/v1/workspaces/{slug}/channels/{id}
    put<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val request = call.receive<UpdateChannelDto>()

        val result = either {
            val channelId = parseId(res.id, "channelId", ::ChannelDirectoryEntryId).bind()
            val existing = channelRepository.findById(channelId) ?: raise(DomainError.ChannelNotFound(channelId))
            val newOverrides = request.metadataOverrides?.toDomain()
            val updated = existing.copy(
                name = request.name ?: existing.name,
                tags = request.tags?.map { Tag(it) }?.toSet() ?: existing.tags,
                metadataOverrides = newOverrides ?: existing.metadataOverrides,
                notes = request.notes ?: existing.notes,
                updatedAt = Clock.System.now(),
            )
            channelRepository.save(updated).bind()
        }

        call.respondEither<ChannelDto, _>(result) { it.toDto() }
    }

    // DELETE /api/v1/workspaces/{slug}/channels/{id}
    delete<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val result = either {
            val channelId = parseId(res.id, "channelId", ::ChannelDirectoryEntryId).bind()
            if (!channelRepository.delete(channelId)) raise(DomainError.ChannelNotFound(channelId))
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}


