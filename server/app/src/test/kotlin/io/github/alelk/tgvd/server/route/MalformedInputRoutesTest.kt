package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.contract.workspace.AddMemberRequestDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/**
 * Malformed input as the server answers it (G10: → `400 VALIDATION_ERROR`). Stage 01.5 (`StatusPages`)
 * turned what the framework cannot parse — a body that is not JSON, a path/query parameter `Resources`
 * cannot convert — into 400. Stage 01.6 moved the job routes' manual parsing into `api:mapping` (400),
 * stage 01.7 the rule, channel and workspace routes' (the blank channel id below was the last 500).
 */
class MalformedInputRoutesTest :
    FunSpec({
        val app = routeTestApp()

        suspend fun HttpResponse.shouldBeError(status: HttpStatusCode, code: String) {
            this.status shouldBe status
            body<ApiErrorDto>().error.code shouldBe code
        }

        beforeSpec {
            app.client.createWorkspace("bad-input")
        }

        test("a broken UUID in the path (jobId, ruleId, channelId) is already 400 VALIDATION_ERROR (parseId)") {
            app.client.get("$WORKSPACES/bad-input/jobs/not-a-uuid") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client.post("$WORKSPACES/bad-input/jobs/not-a-uuid/cancel") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client.get("$WORKSPACES/bad-input/rules/not-a-uuid") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client.get("$WORKSPACES/bad-input/channels/not-a-uuid") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }

        test("a broken UUID in the body (POST …/jobs ruleId) is 400 VALIDATION_ERROR (G10, api:mapping since 01.6)") {
            app.knowsVideo("bad-1")
            val preview =
                app.client
                    .post("$WORKSPACES/bad-input/preview") {
                        asDevUser()
                        jsonBody(PreviewRequestDto(url = videoUrl("bad-1")))
                    }.body<PreviewResponseDto>()
            app.client
                .post("$WORKSPACES/bad-input/jobs") {
                    asDevUser()
                    jsonBody(preview.toCreateJobRequest().copy(ruleId = "not-a-uuid"))
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }

        test("a body that is not JSON is 400 VALIDATION_ERROR (G10, StatusPages since 01.5)") {
            app.client
                .post("$WORKSPACES/bad-input/rules") {
                    asDevUser()
                    jsonBody("{not json")
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }

        test("a JSON body of the wrong shape is 400 VALIDATION_ERROR (G10, StatusPages since 01.5)") {
            app.client
                .post("$WORKSPACES/bad-input/rules") {
                    asDevUser()
                    jsonBody("""{"unexpected": true}""")
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }

        test("a path or query parameter Resources cannot convert is 400 VALIDATION_ERROR (G10, since 01.5)") {
            app.client
                .get("$WORKSPACES/bad-input/jobs?limit=many") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client
                .delete("$WORKSPACES/bad-input/members/not-a-number") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }

        test("a blank value class in the body (channelId) is 400 VALIDATION_ERROR (G10, api:mapping since 01.7)") {
            app.client
                .post("$WORKSPACES/bad-input/channels") {
                    asDevUser()
                    jsonBody(aCreateChannelDto().copy(channelId = " "))
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }

        test("value classes the rule, channel and member routes reject are 400 VALIDATION_ERROR (since 01.7)") {
            app.client
                .post("$WORKSPACES/bad-input/channels") {
                    asDevUser()
                    jsonBody(aCreateChannelDto().copy(tags = listOf("Not A Tag")))
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client
                .get("$WORKSPACES/bad-input/channels?tag=Not%20A%20Tag") { asDevUser() }
                .shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client
                .post("$WORKSPACES/bad-input/rules") {
                    asDevUser()
                    jsonBody(aCreateRuleRequest().copy(match = RuleMatchDto.TitleRegex("(unclosed")))
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            app.client
                .post("$WORKSPACES/bad-input/members") {
                    asDevUser()
                    jsonBody(AddMemberRequestDto(userId = 0))
                }.shouldBeError(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        }
    })
