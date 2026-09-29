package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/**
 * Malformed input as the server answers it TODAY (stage 01.4). G10 turns every `500 INTERNAL_ERROR`
 * below into `400 VALIDATION_ERROR` — the stage that fixes the parsing (01.5 `StatusPages`, 01.6–01.7
 * the routes) changes exactly those expectations, nothing else.
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

        test("a broken UUID in the body (POST …/jobs ruleId) is 500 INTERNAL_ERROR (G10: → 400)") {
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
                }.shouldBeError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
        }

        test("a body that is not JSON is 500 INTERNAL_ERROR (G10: → 400)") {
            app.client
                .post("$WORKSPACES/bad-input/rules") {
                    asDevUser()
                    jsonBody("{not json")
                }.shouldBeError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
        }

        test("a blank value class in the body (channel name) is 500 INTERNAL_ERROR (G10: → 400)") {
            app.client
                .post("$WORKSPACES/bad-input/channels") {
                    asDevUser()
                    jsonBody(aCreateChannelDto().copy(channelId = " "))
                }.shouldBeError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
        }
    })
