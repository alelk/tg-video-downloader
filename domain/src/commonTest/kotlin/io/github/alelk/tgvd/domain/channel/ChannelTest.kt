package io.github.alelk.tgvd.domain.channel

import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ChannelTest : FunSpec({

    fun channel(
        name: String = "Adele",
        tags: Set<Tag> = emptySet(),
        metadataOverrides: MetadataTemplate? = null,
    ): Channel {
        val now = Clock.System.now()
        return Channel(
            id = ChannelDirectoryEntryId(Uuid.random()),
            workspaceId = WorkspaceId(Uuid.random()),
            channelId = ChannelId("UC_adele"),
            extractor = Extractor.YOUTUBE,
            name = name,
            tags = tags,
            metadataOverrides = metadataOverrides,
            createdAt = now,
            updatedAt = now,
        )
    }

    test("valid channel creation") {
        val ch = channel(
            tags = setOf(Tag("music-video"), Tag("pop")),
            metadataOverrides = MetadataTemplate.MusicVideo(artistOverride = "Adele"),
        )
        ch.name shouldBe "Adele"
        ch.tags.size shouldBe 2
        ch.metadataOverrides shouldBe MetadataTemplate.MusicVideo(artistOverride = "Adele")
    }

    test("rejects blank name") {
        shouldThrow<IllegalArgumentException> {
            channel(name = "   ")
        }
    }

    test("allows empty tags") {
        val ch = channel(tags = emptySet())
        ch.tags shouldBe emptySet()
    }

    test("allows null metadataOverrides") {
        val ch = channel(metadataOverrides = null)
        ch.metadataOverrides shouldBe null
    }
})

