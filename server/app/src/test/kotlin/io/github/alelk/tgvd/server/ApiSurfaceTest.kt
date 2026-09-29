package io.github.alelk.tgvd.server

import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.config.ServerConfig
import io.github.alelk.tgvd.server.infra.config.StorageConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.routingRoot
import io.ktor.server.testing.testApplication

/**
 * The HTTP surface, pinned (G1: routes are only ever added). The source of truth is the live routing
 * tree of `module()` booted without a database: every mounted `METHOD path`, sorted, must equal
 * `src/test/resources/api-surface.txt`. A route lost or renamed in a refactor fails here; a new route
 * is added to the file in the same change — that diff is the API changelog.
 *
 * To see it fail: rename any `@Resource` path segment in `api:contract` (e.g. `"jobs"` → `"job"`).
 */
class ApiSurfaceTest :
    FunSpec({
        test("the mounted route table matches api-surface.txt") {
            mountedRoutes().sorted() shouldBe expectedSurface()
        }

        test("the table is not vacuous and no method+path is mounted twice") {
            val routes = mountedRoutes()
            routes.size shouldBeGreaterThanOrEqual ROUTE_COUNT_AT_01_4
            routes.toSet() shouldHaveSize routes.size
        }
    })

/** Mounted routes when the surface was first pinned (stage 01.4). Only grows. */
private const val ROUTE_COUNT_AT_01_4 = 27

private val surfaceConfig =
    AppConfig(
        server = ServerConfig(),
        telegram = TelegramConfig(botToken = "api-surface"),
        db = DbConfig(url = "jdbc:postgresql://localhost:5432/unused", user = "unused", password = "unused"),
        storage = StorageConfig(baseDirectories = listOf("/tmp/tgvd-api-surface")),
    )

private suspend fun mountedRoutes(): List<String> {
    lateinit var routes: List<String>
    testApplication {
        application {
            module(surfaceConfig, eagerDatabase = false, startBackgroundServices = false)
            routes = routingRoot.getAllRoutes().map { it.describe() }
        }
        startApplication()
    }
    return routes
}

/**
 * `/api/v1/workspaces/{workspaceSlug}/jobs/[status?]/(method:GET)` → `GET /api/v1/workspaces/{workspaceSlug}/jobs`.
 * Optional query-parameter selectors (`[name?]`) are not part of the path.
 */
private fun RoutingNode.describe(): String {
    val raw = toString()
    val method = checkNotNull(Regex("""\(method:(\w+)\)""").find(raw)) { "route without a method: $raw" }.groupValues[1]
    val path =
        raw
            .replace(Regex("""/\[[^\]]*\]"""), "")
            .replace(Regex("""/\(method:\w+\)"""), "")
            .replace(Regex("/{2,}"), "/")
            .ifEmpty { "/" }
    return "$method $path"
}

private fun expectedSurface(): List<String> =
    checkNotNull(ApiSurfaceTest::class.java.getResourceAsStream("/api-surface.txt")) { "missing api-surface.txt" }
        .bufferedReader()
        .readLines()
        .filter { it.isNotBlank() }
        .sorted()
