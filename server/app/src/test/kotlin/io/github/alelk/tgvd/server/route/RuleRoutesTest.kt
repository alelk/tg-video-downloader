package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode

/** `…/rules` through the real module over PostgreSQL. */
class RuleRoutesTest :
    FunSpec({
        val app = routeTestApp()

        test("create -> get -> list -> update -> delete") {
            app.client.createWorkspace("rl-happy")
            val request = aCreateRuleRequest()
            val created = app.client.createRule("rl-happy", request)
            created.name shouldBe request.name
            created.enabled shouldBe true
            created.priority shouldBe 5
            created.match shouldBe request.match
            created.category shouldBe CategoryDto.MUSIC_VIDEO
            created.metadataTemplate shouldBe request.metadataTemplate
            created.downloadPolicy shouldBe request.downloadPolicy
            created.outputs shouldBe request.outputs

            val fetched = app.client.get("$WORKSPACES/rl-happy/rules/${created.id}") { asDevUser() }
            fetched.status shouldBe HttpStatusCode.OK
            fetched.body<RuleDto>().copy(createdAt = "", updatedAt = "") shouldBe
                created.copy(createdAt = "", updatedAt = "")

            app.client.get("$WORKSPACES/rl-happy/rules") {
                asDevUser()
            }.body<RuleListResponseDto>().items.map { it.id } shouldBe
                listOf(created.id)

            val update =
                request.copy(
                    name = "Series rule",
                    enabled = false,
                    priority = 1,
                    match = RuleMatchDto.AnyOf(
                        listOf(RuleMatchDto.HasTag("tv"), RuleMatchDto.TitleRegex("S\\d+E\\d+")),
                    ),
                    category = CategoryDto.SERIES_EPISODE,
                    metadataTemplate = MetadataTemplateDto.SeriesEpisode(seriesNameOverride = "Show"),
                )
            val updated =
                app.client.put("$WORKSPACES/rl-happy/rules/${created.id}") {
                    asDevUser()
                    jsonBody(update)
                }
            updated.status shouldBe HttpStatusCode.OK
            updated.body<RuleDto>().let {
                listOf(it.name, it.enabled, it.priority, it.match, it.category, it.metadataTemplate)
            } shouldBe
                listOf(update.name, false, 1, update.match, CategoryDto.SERIES_EPISODE, update.metadataTemplate)

            app.client.delete("$WORKSPACES/rl-happy/rules/${created.id}") { asDevUser() }.status shouldBe
                HttpStatusCode.NoContent
            app.client.get("$WORKSPACES/rl-happy/rules/${created.id}") { asDevUser() }.status shouldBe
                HttpStatusCode.NotFound
        }

        test("a rule of another workspace is 404 NOT_FOUND through get, update and delete") {
            app.client.createWorkspace("rl-mine")
            val other = signedInitData(8, "eighth")
            app.client.createWorkspace("rl-theirs", initData = other)
            val foreign = app.client.createRule("rl-theirs", initData = other)

            listOf(
                app.client.get("$WORKSPACES/rl-mine/rules/${foreign.id}") { asDevUser() },
                app.client.put("$WORKSPACES/rl-mine/rules/${foreign.id}") {
                    asDevUser()
                    jsonBody(aCreateRuleRequest(name = "hijack"))
                },
                app.client.delete("$WORKSPACES/rl-mine/rules/${foreign.id}") { asDevUser() },
            ).forEach { response ->
                response.status shouldBe HttpStatusCode.NotFound
                response.body<ApiErrorDto>().error.code shouldBe "NOT_FOUND"
            }
            app.client.get("$WORKSPACES/rl-theirs/rules/${foreign.id}") {
                headers.append(INIT_DATA_HEADER, other)
            }.body<RuleDto>().name shouldBe foreign.name
        }
    })
