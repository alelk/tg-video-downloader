package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.workspace.AddMemberRequestDto
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceListResponseDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceMemberDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceMemberListResponseDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonPrimitive

/** Auth and `/workspaces` through the real module over PostgreSQL. */
class WorkspaceRoutesTest :
    FunSpec({
        val app = routeTestApp()

        test("health answers without authentication") {
            val response = app.client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"ok"}"""
        }

        test("an API call without X-Telegram-Init-Data is 401 UNAUTHORIZED") {
            val response = app.client.get(WORKSPACES)
            response.status shouldBe HttpStatusCode.Unauthorized
            val error = response.body<ApiErrorDto>().error
            error.code shouldBe "UNAUTHORIZED"
            error.correlationId.shouldNotBeBlank()
        }

        test("initData with a wrong signature is 401 UNAUTHORIZED") {
            val response =
                app.client.get(WORKSPACES) {
                    headers.append(INIT_DATA_HEADER, signedInitData(7, "mallory", botToken = "999:another-bot"))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ApiErrorDto>().error.code shouldBe "UNAUTHORIZED"
        }

        test("create -> list -> members: the creator is the owner") {
            val created = app.client.createWorkspace("ws-owner")
            created.slug shouldBe "ws-owner"
            created.name shouldBe "Workspace ws-owner"
            created.role shouldBe "owner"

            val list = app.client.get(WORKSPACES) { asDevUser() }
            list.status shouldBe HttpStatusCode.OK
            // createdAt differs: POST answers with the use-case clock, reads return the database default
            // (found in 01.4, kept as current behaviour).
            list.body<WorkspaceListResponseDto>().items.map { it.copy(createdAt = "") } shouldContain
                created.copy(createdAt = "")

            val members = app.client.get("$WORKSPACES/ws-owner/members") { asDevUser() }
            members.status shouldBe HttpStatusCode.OK
            members.body<WorkspaceMemberListResponseDto>().items.map { it.userId to it.role } shouldBe
                listOf(DEV_USER_ID to "owner")
        }

        test("POST /workspaces with a taken slug is 200 and makes the caller a MEMBER (known issue, kept)") {
            app.client.createWorkspace("ws-shared")
            val joined = app.client.createWorkspace(
                "ws-shared",
                initData = signedInitData(2, "second"),
                expected = HttpStatusCode.OK,
            )
            joined.role shouldBe "member"

            val again = app.client.createWorkspace("ws-shared", expected = HttpStatusCode.OK)
            again.role shouldBe "owner"

            val members = app.client.get("$WORKSPACES/ws-shared/members") {
                asDevUser()
            }.body<WorkspaceMemberListResponseDto>()
            members.items.map { it.userId to it.role } shouldContainExactlyInAnyOrder
                listOf(DEV_USER_ID to "owner", 2L to "member")
        }

        test("POST /workspaces with an invalid slug is 400 with a bare {\"error\": …} body, not ApiErrorDto") {
            val response =
                app.client.post(WORKSPACES) {
                    asDevUser()
                    jsonBody(CreateWorkspaceRequestDto(slug = "Bad Slug!", name = "x"))
                }
            response.status shouldBe HttpStatusCode.BadRequest
            val body = response.jsonObject()
            body.keys shouldBe setOf("error")
            body.getValue("error").jsonPrimitive.content.shouldNotBeBlank()
        }

        test("members: the owner adds and removes; a non-owner is 403; removing a non-member is 400") {
            app.client.createWorkspace("ws-members")
            val added =
                app.client.post("$WORKSPACES/ws-members/members") {
                    asDevUser()
                    jsonBody(AddMemberRequestDto(userId = 3, role = "member"))
                }
            added.status shouldBe HttpStatusCode.Created
            added.body<WorkspaceMemberDto>().let { it.userId to it.role } shouldBe (3L to "member")

            val byMember =
                app.client.post("$WORKSPACES/ws-members/members") {
                    asTelegramUser(3)
                    jsonBody(AddMemberRequestDto(userId = 4))
                }
            byMember.status shouldBe HttpStatusCode.Forbidden
            byMember.body<ApiErrorDto>().error.code shouldBe "FORBIDDEN"

            app.client.delete("$WORKSPACES/ws-members/members/3") { asDevUser() }.status shouldBe
                HttpStatusCode.NoContent
            val again = app.client.delete("$WORKSPACES/ws-members/members/3") { asDevUser() }
            again.status shouldBe HttpStatusCode.BadRequest
            again.body<ApiErrorDto>().error.code shouldBe "VALIDATION_ERROR"
        }

        test("a workspace of someone else is 403 FORBIDDEN; an unknown slug is 404 NOT_FOUND") {
            app.client.createWorkspace("ws-private", initData = signedInitData(5, "fifth"))

            val foreign = app.client.get("$WORKSPACES/ws-private/members") { asDevUser() }
            foreign.status shouldBe HttpStatusCode.Forbidden
            foreign.body<ApiErrorDto>().error.code shouldBe "FORBIDDEN"

            val unknown = app.client.get("$WORKSPACES/ws-unknown/rules") { asDevUser() }
            unknown.status shouldBe HttpStatusCode.NotFound
            unknown.body<ApiErrorDto>().error.code shouldBe "NOT_FOUND"
        }
    })
