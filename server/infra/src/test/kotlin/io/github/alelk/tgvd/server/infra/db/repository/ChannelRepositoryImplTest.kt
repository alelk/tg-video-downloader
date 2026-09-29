package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.storage.TrackPreferences
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.T0
import io.github.alelk.tgvd.server.infra.db.fixtures.aChannel
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Round-trips of [ChannelRepositoryImpl] on PostgreSQL, every call under [ExposedTransactionRunner]. */
class ChannelRepositoryImplTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val workspaces = WorkspaceRepositoryImpl(db)
        val repository = ChannelRepositoryImpl(db)

        fun Channel.withoutTimestamps() = copy(createdAt = T0, updatedAt = T0)

        test("save -> find -> update -> find") {
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            val channel = aChannel(workspace.id)

            tx.inRwTransaction { repository.save(channel) }.shouldBeRight() shouldBe channel
            tx.inRoTransaction { repository.findById(channel.id) }.shouldNotBeNull().withoutTimestamps() shouldBe
                channel

            val updated =
                channel.copy(
                    name = "Rick Astley VEVO",
                    tags = setOf(Tag("vevo")),
                    metadataOverrides = MetadataTemplate.Other(titlePattern = "(.+)", defaultTags = listOf("x")),
                    notes = null,
                    trackPreferences = TrackPreferences(),
                )
            tx.inRwTransaction { repository.save(updated) }.shouldBeRight()
            tx.inRoTransaction { repository.findById(channel.id) }.shouldNotBeNull().withoutTimestamps() shouldBe
                updated

            val cleared = updated.copy(metadataOverrides = null, trackPreferences = null)
            tx.inRwTransaction { repository.save(cleared) }.shouldBeRight()
            tx.inRoTransaction { repository.findById(channel.id) }.shouldNotBeNull().withoutTimestamps() shouldBe
                cleared
        }

        test("lookups by channel id, tag and tags; all tags; delete") {
            val workspace = aWorkspace()
            val other = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            tx.inRwTransaction { workspaces.save(other) }.shouldBeRight()
            val music = aChannel(workspace.id, channelId = "UC-music", tags = setOf("music", "pop"))
            val news = aChannel(workspace.id, channelId = "UC-news", tags = setOf("news"))
            val foreign = aChannel(other.id, channelId = "UC-music", tags = setOf("music"))
            listOf(music, news, foreign).forEach { tx.inRwTransaction { repository.save(it) }.shouldBeRight() }

            tx.inRoTransaction { repository.findByChannelId(workspace.id, ChannelId("UC-music"), Extractor.YOUTUBE) }
                .shouldNotBeNull()
                .id shouldBe music.id
            tx.inRoTransaction {
                repository.findByChannelId(workspace.id, ChannelId("UC-music"), Extractor.VK)
            }.shouldBeNull()
            tx.inRoTransaction { repository.findByWorkspace(workspace.id) }.map { it.id } shouldContainExactlyInAnyOrder
                listOf(music.id, news.id)
            tx.inRoTransaction { repository.findByTag(workspace.id, Tag("music")) }.map { it.id } shouldBe
                listOf(music.id)
            tx.inRoTransaction {
                repository.findByTags(workspace.id, setOf(Tag("music"), Tag("news")), matchAll = false)
            }
                .map { it.id } shouldContainExactlyInAnyOrder listOf(music.id, news.id)
            tx.inRoTransaction { repository.findByTags(workspace.id, setOf(Tag("music"), Tag("pop")), matchAll = true) }
                .map { it.id } shouldBe listOf(music.id)
            tx.inRoTransaction { repository.findAllTags(workspace.id) } shouldBe
                setOf(Tag("music"), Tag("pop"), Tag("news"))

            tx.inRwTransaction { repository.delete(news.id) }.shouldBeTrue()
            tx.inRwTransaction { repository.delete(news.id) }.shouldBeFalse()
            tx.inRoTransaction { repository.findById(news.id) }.shouldBeNull()
        }
    })
