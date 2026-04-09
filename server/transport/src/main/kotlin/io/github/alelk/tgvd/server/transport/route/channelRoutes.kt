package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.channel.ChannelListResponseDto
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.TagListResponseDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.channel.toDto
import io.github.alelk.tgvd.api.mapping.channel.toDomain
import io.github.alelk.tgvd.domain.channel.*
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.parseId
import io.github.alelk.tgvd.server.transport.util.requireWorkspaceMember
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.delete
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Route.channelRoutes() {
    val createChannel by inject<CreateChannelUseCase>()
    val updateChannel by inject<UpdateChannelUseCase>()
    val deleteChannel by inject<DeleteChannelUseCase>()
    val channelRepository by inject<ChannelRepository>()
    val workspaceRepository by inject<WorkspaceRepository>()

    get<ApiV1.Workspaces.ById.Channels> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val channels =
                when {
                    res.channelId != null && res.extractor != null ->
                        listOfNotNull(
                            channelRepository
                                .findByChannelId(
                                    ws.id,
                                    ChannelId(res.channelId!!),
                                    Extractor(res.extractor!!)
                                )
                        )

                    res.tag != null ->
                        channelRepository.findByTag(ws.id, Tag(res.tag!!))

                    else ->
                        channelRepository.findByWorkspace(ws.id)
                }
            ChannelListResponseDto(items = channels.map { it.toDto() })
        }
        call.respondEither(result)
    }

    get<ApiV1.Workspaces.ById.Channels.Tags> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            TagListResponseDto(tags = channelRepository.findAllTags(ws.id).map { it.value }.sorted())
        }
        call.respondEither(result)
    }

    post<ApiV1.Workspaces.ById.Channels> { res ->
        val request = call.receive<CreateChannelDto>()
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            createChannel(request.toDomain(ws.id)).bind()
        }
        call.respondEither<ChannelDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    get<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val channelId = parseId(res.id, "channelId", ::ChannelDirectoryEntryId).bind()
            val channel = channelRepository.findById(channelId) ?: raise(DomainError.ChannelNotFound(channelId))
            if (channel.workspaceId != ws.id) raise(DomainError.ChannelNotFound(channelId))
            channel
        }
        call.respondEither<ChannelDto, _>(result) { it.toDto() }
    }

    put<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val request = call.receive<UpdateChannelDto>()
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val channelId = parseId(res.id, "channelId", ::ChannelDirectoryEntryId).bind()
            val existing = channelRepository.findById(channelId) ?: raise(DomainError.ChannelNotFound(channelId))
            if (existing.workspaceId != ws.id) raise(DomainError.ChannelNotFound(channelId))
            updateChannel(channelId, request.toDomain()).bind()
        }
        call.respondEither<ChannelDto, _>(result) { it.toDto() }
    }

    delete<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val channelId = parseId(res.id, "channelId", ::ChannelDirectoryEntryId).bind()
            val existing = channelRepository.findById(channelId) ?: raise(DomainError.ChannelNotFound(channelId))
            if (existing.workspaceId != ws.id) raise(DomainError.ChannelNotFound(channelId))
            deleteChannel(channelId).bind()
        }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}


