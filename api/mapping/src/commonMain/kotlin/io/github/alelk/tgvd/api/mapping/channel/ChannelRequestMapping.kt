package io.github.alelk.tgvd.api.mapping.channel

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.mapping.common.parseId
import io.github.alelk.tgvd.api.mapping.common.parseValue
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.domain.channel.ChannelFilter
import io.github.alelk.tgvd.domain.channel.CreateChannelRequest
import io.github.alelk.tgvd.domain.channel.UpdateChannelRequest
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import kotlin.uuid.ExperimentalUuidApi

/** Parses the `channelId` path parameter (the directory entry id, a UUID). */
@OptIn(ExperimentalUuidApi::class)
fun parseChannelEntryId(raw: String): Either<DomainError.ValidationError, ChannelDirectoryEntryId> =
    parseId(raw, "channelId", ::ChannelDirectoryEntryId)

/**
 * The query of `GET …/channels`: `channelId` together with `extractor` → one channel; otherwise `tag`
 * → the tagged channels; otherwise all. A lone `channelId` or `extractor` is ignored.
 */
fun parseChannelFilter(
    channelId: String?,
    extractor: String?,
    tag: String?,
): Either<DomainError.ValidationError, ChannelFilter> = either {
    when {
        channelId != null && extractor != null ->
            ChannelFilter.ByPlatformId(
                channelId = parseValue("channelId") { ChannelId(channelId) }.bind(),
                extractor = parseValue("extractor") { Extractor(extractor) }.bind(),
            )

        tag != null -> ChannelFilter.ByTag(parseValue("tag") { Tag(tag) }.bind())

        else -> ChannelFilter.All
    }
}

/** `POST …/channels` body → [CreateChannelRequest]; the workspace comes from the path, via the use-case. */
fun CreateChannelDto.toDomain(): Either<DomainError.ValidationError, CreateChannelRequest> = either {
    CreateChannelRequest(
        channelId = parseValue("channelId") { ChannelId(channelId) }.bind(),
        extractor = parseValue("extractor") { Extractor(extractor) }.bind(),
        name = name,
        tags = tags.toDomainTags().bind(),
        metadataOverrides = metadataOverrides?.toDomain(),
        notes = notes,
        trackPreferences = trackPreferences?.toDomain(),
    )
}

/** `PUT …/channels/{id}` body → [UpdateChannelRequest]. */
fun UpdateChannelDto.toDomain(): Either<DomainError.ValidationError, UpdateChannelRequest> = either {
    UpdateChannelRequest(
        name = name,
        tags = tags?.toDomainTags()?.bind(),
        metadataOverrides = metadataOverrides?.toDomain(),
        notes = notes,
        trackPreferences = trackPreferences?.toDomain(),
    )
}

private fun List<String>.toDomainTags(): Either<DomainError.ValidationError, Set<Tag>> = either {
    map { parseValue("tags") { Tag(it) }.bind() }.toSet()
}
