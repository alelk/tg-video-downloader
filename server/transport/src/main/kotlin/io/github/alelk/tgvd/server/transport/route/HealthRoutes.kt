package io.github.alelk.tgvd.server.transport.route

import io.github.alelk.tgvd.domain.system.ReadinessProbe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Public probes — mounted outside the Telegram auth.
 *
 * - `GET /health` — the legacy probe (Docker `HEALTHCHECK`, compose): `{"status":"ok"}` while the
 *   process answers. Byte-for-byte unchanged (G1).
 * - `GET /health/live` — liveness: `200 {"status":"live"}`, fails only with a dead process.
 * - `GET /health/ready` — readiness: `200 {"status":"ready"}` when every [probes] says ready,
 *   otherwise `503 {"status":"not ready"}`.
 */
fun Route.healthRoutes(probes: List<ReadinessProbe>) {
    get("/health") {
        call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
    }
    get("/health/live") {
        call.respondText("""{"status":"live"}""", ContentType.Application.Json)
    }
    get("/health/ready") {
        val ready = probes.all { it.isReady() }
        if (ready) {
            call.respondText("""{"status":"ready"}""", ContentType.Application.Json)
        } else {
            call.respondText(
                """{"status":"not ready"}""",
                ContentType.Application.Json,
                HttpStatusCode.ServiceUnavailable,
            )
        }
    }
}
