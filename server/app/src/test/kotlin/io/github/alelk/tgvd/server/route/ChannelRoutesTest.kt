package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.channel.ChannelListResponseDto
import io.github.alelk.tgvd.api.contract.channel.TagListResponseDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.storage.TrackPreferencesDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode

/** `…/channels` through the real module over PostgreSQL. */
class ChannelRoutesTest :
    FunSpec({
        val app = routeTestApp()

        suspend fun HttpClient.createChannel(
            slug: String,
            channelId: String,
            tags: List<String>,
            initData: String = "dev",
        ): ChannelDto {
            val response =
                post("$WORKSPACES/$slug/channels") {
                    headers.append(INIT_DATA_HEADER, initData)
                    jsonBody(aCreateChannelDto(channelId, tags))
                }
            response.status shouldBe HttpStatusCode.Created
            return response.body()
        }

        test("create -> get -> filters -> tags -> update -> delete") {
            app.client.createWorkspace("ch-happy")
            val music = app.client.createChannel("ch-happy", "UC-music", listOf("music", "pop"))
            val news = app.client.createChannel("ch-happy", "UC-news", listOf("news"))
            music.channelId shouldBe "UC-music"
            music.extractor shouldBe "youtube"
            music.name shouldBe "Channel UC-music"
            music.tags shouldContainExactlyInAnyOrder listOf("music", "pop")
            music.metadataOverrides shouldBe MetadataTemplateDto.Other(defaultTags = listOf("from-channel"))
            music.notes shouldBe "notes"
            music.trackPreferences shouldBe TrackPreferencesDto(audioLanguages = emptyList())

            app.client.get("$WORKSPACES/ch-happy/channels/${music.id}") { asDevUser() }.body<ChannelDto>().id shouldBe
                music.id

            val all = app.client.get("$WORKSPACES/ch-happy/channels") { asDevUser() }.body<ChannelListResponseDto>()
            all.items.map { it.id } shouldContainExactlyInAnyOrder listOf(music.id, news.id)
            app.client
                .get("$WORKSPACES/ch-happy/channels?tag=news") { asDevUser() }
                .body<ChannelListResponseDto>()
                .items
                .map { it.id } shouldBe listOf(news.id)
            app.client
                .get("$WORKSPACES/ch-happy/channels?channelId=UC-music&extractor=youtube") { asDevUser() }
                .body<ChannelListResponseDto>()
                .items
                .map { it.id } shouldBe listOf(music.id)
            app.client.get("$WORKSPACES/ch-happy/channels/tags") {
                asDevUser()
            }.body<TagListResponseDto>().tags shouldBe
                listOf("music", "news", "pop")

            val updated =
                app.client.put("$WORKSPACES/ch-happy/channels/${music.id}") {
                    asDevUser()
                    jsonBody(UpdateChannelDto(name = "Renamed", tags = listOf("vevo"), notes = "new notes"))
                }
            updated.status shouldBe HttpStatusCode.OK
            updated.body<ChannelDto>().let { Triple(it.name, it.tags, it.notes) } shouldBe
                Triple("Renamed", listOf("vevo"), "new notes")

            app.client.delete("$WORKSPACES/ch-happy/channels/${news.id}") { asDevUser() }.status shouldBe
                HttpStatusCode.NoContent
            app.client.get("$WORKSPACES/ch-happy/channels/${news.id}") { asDevUser() }.status shouldBe
                HttpStatusCode.NotFound
        }

        test("a channel of another workspace is 404 NOT_FOUND through get, update and delete") {
            app.client.createWorkspace("ch-mine")
            val other = signedInitData(9, "ninth")
            app.client.createWorkspace("ch-theirs", initData = other)
            val foreign = app.client.createChannel("ch-theirs", "UC-foreign", listOf("x"), initData = other)

            listOf(
                app.client.get("$WORKSPACES/ch-mine/channels/${foreign.id}") { asDevUser() },
                app.client.put("$WORKSPACES/ch-mine/channels/${foreign.id}") {
                    asDevUser()
                    jsonBody(UpdateChannelDto(name = "hijack"))
                },
                app.client.delete("$WORKSPACES/ch-mine/channels/${foreign.id}") { asDevUser() },
            ).forEach { response ->
                response.status shouldBe HttpStatusCode.NotFound
                response.body<ApiErrorDto>().error.code shouldBe "NOT_FOUND"
            }
        }
    })
