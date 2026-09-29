package io.github.alelk.tgvd.api.client

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceListResponseDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import java.io.IOException

class TgVideoDownloaderClientImplTest :
    FunSpec({
        test("2xx: the body is decoded and initData is read on every request") {
            var initData = "first"
            val seen = mutableListOf<String?>()
            val client =
                client(initDataProvider = { initData }) { request ->
                    seen += request.headers["X-Telegram-Init-Data"]
                    respondJson(HttpStatusCode.OK, WORKSPACES_JSON)
                }

            client.getWorkspaces() shouldBe WorkspaceListResponseDto(listOf(WORKSPACE)).right()
            initData = "second"
            client.getWorkspaces()

            seen shouldBe listOf("first", "second")
        }

        test("the workspace slug scopes workspace endpoints") {
            var path: String? = null
            val client =
                client { request ->
                    path = request.url.encodedPath
                    respondJson(HttpStatusCode.OK, """{"items":[],"total":0,"limit":20,"offset":0}""")
                }
            client.workspaceSlug = "family"

            client.getJobs()

            path shouldBe "/api/v1/workspaces/family/jobs"
        }

        test("non-2xx with an ApiErrorDto body -> ApiError.Http with the server's code, message and correlation id") {
            val client =
                client {
                    respondJson(
                        HttpStatusCode.NotFound,
                        """{"error":{"code":"NOT_FOUND","message":"Rule 42 not found","correlationId":"req-1"}}""",
                    )
                }

            client.getRule("42") shouldBe
                ApiError.Http(status = 404, code = "NOT_FOUND", message = "Rule 42 not found", correlationId = "req-1")
                    .left()
        }

        test("non-2xx without an ApiErrorDto body -> ApiError.Http HTTP_<status> quoting the body") {
            val client =
                client {
                    respond(
                        "<html>upstream down</html>",
                        HttpStatusCode.BadGateway,
                        headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            client.getSettings() shouldBe
                ApiError.Http(
                    status = 502,
                    code = "HTTP_502",
                    message = "502 Bad Gateway: <html>upstream down</html>",
                    correlationId = "",
                ).left()
        }

        test("the request fails -> ApiError.Network with the failure's message") {
            val client = client { throw IOException("Connection refused") }

            val error = client.getWorkspaces().leftOrNull().shouldBeInstanceOf<ApiError.Network>()
            error.message shouldBe "Connection refused"
            error.cause.shouldBeInstanceOf<IOException>()
        }

        test("a kotlin.Error from the engine (Ktor JS: failed fetch) -> ApiError.Network, not a crash") {
            val client = client { failFetch() }

            val error = client.getJobs().leftOrNull().shouldBeInstanceOf<ApiError.Network>()
            error.message shouldBe "Fail to fetch"
        }

        test("2xx with a broken JSON body -> ApiError.Decoding") {
            val client = client { respondJson(HttpStatusCode.OK, """{"items":[{"id":""") }

            client.getWorkspaces().leftOrNull().shouldBeInstanceOf<ApiError.Decoding>()
        }

        test("2xx with a body of the wrong shape -> ApiError.Decoding") {
            val client = client { respondJson(HttpStatusCode.OK, """{"unexpected":true}""") }

            client.getWorkspaces().leftOrNull().shouldBeInstanceOf<ApiError.Decoding>()
        }

        test("delete: 204 -> Unit, an error status -> ApiError.Http") {
            var status = HttpStatusCode.NoContent
            var method: HttpMethod? = null
            val client =
                client { request ->
                    method = request.method
                    if (status == HttpStatusCode.NoContent) {
                        respond("", status)
                    } else {
                        respondJson(
                            status,
                            """{"error":{"code":"FORBIDDEN","message":"No access","correlationId":"c"}}""",
                        )
                    }
                }

            client.deleteChannel("7") shouldBe Unit.right()
            method shouldBe HttpMethod.Delete

            status = HttpStatusCode.Forbidden
            client.deleteRule("7") shouldBe ApiError.Http(403, "FORBIDDEN", "No access", "c").left()
        }

        test("cancellation is not turned into an error value") {
            val client = client { throw CancellationException("screen left") }

            shouldThrow<CancellationException> { client.getWorkspaces() }
        }
    })

private val WORKSPACE =
    WorkspaceDto(id = "w1", slug = "default", name = "Default", role = "owner", createdAt = "2026-01-01T00:00:00Z")

private const val WORKSPACES_JSON =
    """{"items":[{"id":"w1","slug":"default","name":"Default","role":"owner","createdAt":"2026-01-01T00:00:00Z"}]}"""

private fun client(
    initDataProvider: () -> String = { "dev" },
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): TgVideoDownloaderClientImpl = TgVideoDownloaderClientImpl(
    httpClient = HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(apiJson) } },
    baseUrl = "http://localhost/",
    initDataProvider = initDataProvider,
)

/** What the Ktor JS engine throws when `fetch` fails: a plain `kotlin.Error`, not an `Exception`. */
@Suppress("TooGenericExceptionThrown") // reproduces the engine's exact failure type
private fun failFetch(): Nothing = throw Error("Fail to fetch")

private fun MockRequestHandleScope.respondJson(status: HttpStatusCode, json: String): HttpResponseData =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))
