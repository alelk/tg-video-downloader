package io.github.alelk.tgvd.domain.channel

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.fakes.FakeChannelRepository
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.aChannel
import io.github.alelk.tgvd.domain.fixtures.aCreateChannelRequest
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.storage.TrackPreferences
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

/** The workspace-scoped channel directory use-cases. */
class ChannelUseCasesTest :
    FunSpec({
        val alice = TelegramUserId(1)
        val bob = TelegramUserId(2)

        class Env {
            val clock = TestClock()
            val workspaces = FakeWorkspaceRepository()
            val channels = FakeChannelRepository()
            val home = aWorkspace("home").also { workspaces.seed(it, aMember(it, alice)) }
            val work = aWorkspace("work").also { workspaces.seed(it, aMember(it, bob)) }
            private val access = WorkspaceAccess(workspaces)
            private val tx = NoopTransactionRunner()
            val listChannels = ListChannelsUseCase(access, channels, tx)
            val listTags = ListChannelTagsUseCase(access, channels, tx)
            val getChannel = GetChannelUseCase(access, channels, tx)
            val createChannel = CreateChannelUseCase(access, channels, tx, clock)
            val updateChannel = UpdateChannelUseCase(access, channels, tx, clock)
            val deleteChannel = DeleteChannelUseCase(access, channels, tx)
        }

        context("ListChannelsUseCase") {
            val env = Env()
            val music = env.channels.seed(aChannel(env.home, "UC-music", setOf("music", "pop")))
            val news = env.channels.seed(aChannel(env.home, "UC-news", setOf("news")))
            env.channels.seed(aChannel(env.work, "UC-music", setOf("music")))

            test("All lists the workspace's channels only") {
                env.listChannels(env.home.slug, alice, ChannelFilter.All).shouldBeRight() shouldContainExactlyInAnyOrder
                    listOf(music, news)
            }

            test("ByPlatformId finds the one channel of this workspace, or nothing") {
                env.listChannels(
                    env.home.slug,
                    alice,
                    ChannelFilter.ByPlatformId(ChannelId("UC-music"), Extractor.YOUTUBE),
                ) shouldBe listOf(music).right()
                env.listChannels(
                    env.home.slug,
                    alice,
                    ChannelFilter.ByPlatformId(ChannelId("UC-music"), Extractor.VK),
                ) shouldBe emptyList<Channel>().right()
            }

            test("ByTag lists the workspace's channels with the tag") {
                env.listChannels(env.home.slug, alice, ChannelFilter.ByTag(Tag("news"))) shouldBe listOf(news).right()
            }

            test("a non-member is WorkspaceAccessDenied") {
                env.listChannels(env.home.slug, bob, ChannelFilter.All) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
            }
        }

        context("ListChannelTagsUseCase") {
            test("the workspace's tags, once each, sorted by value") {
                val env = Env()
                env.channels.seed(aChannel(env.home, "UC-1", setOf("pop", "music")))
                env.channels.seed(aChannel(env.home, "UC-2", setOf("news", "music")))
                env.channels.seed(aChannel(env.work, "UC-3", setOf("alien")))
                env.listTags(env.home.slug, alice) shouldBe listOf(Tag("music"), Tag("news"), Tag("pop")).right()
            }
        }

        context("GetChannelUseCase") {
            test("reads a channel of the workspace; a channel of another workspace is ChannelNotFound") {
                val env = Env()
                val mine = env.channels.seed(aChannel(env.home))
                val foreign = env.channels.seed(aChannel(env.work))
                env.getChannel(env.home.slug, alice, mine.id) shouldBe mine.right()
                env.getChannel(env.home.slug, alice, foreign.id) shouldBe DomainError.ChannelNotFound(foreign.id).left()
            }
        }

        context("CreateChannelUseCase") {
            test("creates the channel in the caller's workspace at the clock's time") {
                val env = Env()
                val created =
                    env.createChannel(env.home.slug, alice, aCreateChannelRequest("UC-new", setOf("music")))
                        .shouldBeRight()
                created.workspaceId shouldBe env.home.id
                created.channelId shouldBe ChannelId("UC-new")
                created.tags shouldBe setOf(Tag("music"))
                created.createdAt shouldBe env.clock.now()
                env.channels.findById(created.id) shouldBe created
            }

            test("a non-member cannot create a channel") {
                val env = Env()
                env.createChannel(env.home.slug, bob, aCreateChannelRequest()) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
                env.channels.findByWorkspace(env.home.id) shouldBe emptyList()
            }
        }

        context("UpdateChannelUseCase") {
            test("overwrites only the given fields; empty track preferences clear the overrides") {
                val env = Env()
                val channel =
                    env.channels.seed(
                        aChannel(env.home, tags = setOf("pop"))
                            .copy(trackPreferences = TrackPreferences(audioLanguages = listOf("en"))),
                    )
                env.clock.advance(1.minutes)
                val updated =
                    env.updateChannel(
                        env.home.slug,
                        alice,
                        channel.id,
                        UpdateChannelRequest(name = "Renamed", trackPreferences = TrackPreferences()),
                    ).shouldBeRight()
                updated shouldBe
                    channel.copy(name = "Renamed", trackPreferences = null, updatedAt = env.clock.now())
            }

            test("a channel of another workspace is ChannelNotFound and stays unchanged") {
                val env = Env()
                val foreign = env.channels.seed(aChannel(env.work))
                env.updateChannel(env.home.slug, alice, foreign.id, UpdateChannelRequest(name = "hijack")) shouldBe
                    DomainError.ChannelNotFound(foreign.id).left()
                env.channels.findById(foreign.id) shouldBe foreign
            }
        }

        context("DeleteChannelUseCase") {
            test("deletes a channel of the workspace; a channel of another workspace is ChannelNotFound") {
                val env = Env()
                val mine = env.channels.seed(aChannel(env.home))
                val foreign = env.channels.seed(aChannel(env.work))
                env.deleteChannel(env.home.slug, alice, mine.id) shouldBe Unit.right()
                env.channels.findById(mine.id) shouldBe null
                env.deleteChannel(env.home.slug, alice, foreign.id) shouldBe
                    DomainError.ChannelNotFound(foreign.id).left()
                env.channels.findById(foreign.id) shouldBe foreign
            }
        }
    })
