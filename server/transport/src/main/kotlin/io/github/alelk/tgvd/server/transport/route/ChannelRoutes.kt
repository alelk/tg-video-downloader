package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.channel.ChannelListResponseDto
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.TagListResponseDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.channel.parseChannelEntryId
import io.github.alelk.tgvd.api.mapping.channel.parseChannelFilter
import io.github.alelk.tgvd.api.mapping.channel.toDomain
import io.github.alelk.tgvd.api.mapping.channel.toDto
import io.github.alelk.tgvd.domain.channel.CreateChannelUseCase
import io.github.alelk.tgvd.domain.channel.DeleteChannelUseCase
import io.github.alelk.tgvd.domain.channel.GetChannelUseCase
import io.github.alelk.tgvd.domain.channel.ListChannelTagsUseCase
import io.github.alelk.tgvd.domain.channel.ListChannelsUseCase
import io.github.alelk.tgvd.domain.channel.UpdateChannelUseCase
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.channelRoutes() {
    channelCollectionRoutes()
    channelByIdRoutes()
}

/** `GET …/channels` (filters `channelId`+`extractor`, `tag`), `GET …/channels/tags`, `POST …/channels`. */
private fun Route.channelCollectionRoutes() {
    val listChannels by inject<ListChannelsUseCase>()
    val listTags by inject<ListChannelTagsUseCase>()
    val createChannel by inject<CreateChannelUseCase>()

    get<ApiV1.Workspaces.ById.Channels> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                val filter = parseChannelFilter(res.channelId, res.extractor, res.tag).bind()
                listChannels(slug, call.telegramUser.id, filter).bind()
            }
        call.respondEither<ChannelListResponseDto, _>(result) { channels ->
            ChannelListResponseDto(items = channels.map { it.toDto() })
        }
    }

    get<ApiV1.Workspaces.ById.Channels.Tags> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                listTags(slug, call.telegramUser.id).bind()
            }
        call.respondEither<TagListResponseDto, _>(result) { tags -> TagListResponseDto(tags = tags.map { it.value }) }
    }

    post<ApiV1.Workspaces.ById.Channels> { res ->
        val body = call.receive<CreateChannelDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                val request = body.toDomain().bind()
                createChannel(slug, call.telegramUser.id, request).bind()
            }
        call.respondEither<ChannelDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }
}

/** `GET`, `PUT` and `DELETE …/channels/{id}`. */
private fun Route.channelByIdRoutes() {
    val getChannel by inject<GetChannelUseCase>()
    val updateChannel by inject<UpdateChannelUseCase>()
    val deleteChannel by inject<DeleteChannelUseCase>()

    get<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val channelId = parseChannelEntryId(res.id).bind()
                getChannel(slug, call.telegramUser.id, channelId).bind()
            }
        call.respondEither<ChannelDto, _>(result) { it.toDto() }
    }

    put<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val body = call.receive<UpdateChannelDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val channelId = parseChannelEntryId(res.id).bind()
                val request = body.toDomain().bind()
                updateChannel(slug, call.telegramUser.id, channelId, request).bind()
            }
        call.respondEither<ChannelDto, _>(result) { it.toDto() }
    }

    delete<ApiV1.Workspaces.ById.Channels.ById> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val channelId = parseChannelEntryId(res.id).bind()
                deleteChannel(slug, call.telegramUser.id, channelId).bind()
            }
        call.respondEither(result, HttpStatusCode.NoContent)
    }
}
