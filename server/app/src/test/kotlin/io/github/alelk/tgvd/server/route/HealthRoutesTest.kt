package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.github.alelk.tgvd.server.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

/** `/health` (unchanged), `/health/live`, `/health/ready` — public, readiness checks the database. */
class HealthRoutesTest :
    FunSpec({
        val app = routeTestApp()

        test("/health is byte-for-byte the legacy answer, without authentication") {
            val response = app.client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"ok"}"""
        }

        test("/health/live is 200 without authentication") {
            val response = app.client.get("/health/live")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"live"}"""
        }

        test("/health/ready is 200 while the database answers") {
            val response = app.client.get("/health/ready")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"status":"ready"}"""
        }

        // Last in the spec: the database of this spec is gone afterwards.
        test("/health/ready is 503 once the database is unreachable; /health and /health/live stay 200") {
            PostgresTestContainer.dropDatabase(app.dbConfig)

            val ready = app.client.get("/health/ready")
            ready.status shouldBe HttpStatusCode.ServiceUnavailable
            ready.bodyAsText() shouldBe """{"status":"not ready"}"""
            app.client.get("/health").status shouldBe HttpStatusCode.OK
            app.client.get("/health/live").status shouldBe HttpStatusCode.OK
        }
    })

/** A server booted without a database ([module] with `eagerDatabase = false`) is honestly not ready. */
class HealthWithoutDatabaseTest :
    FunSpec({
        test("/health/ready is 503 when the server was started without a database") {
            testApplication {
                application {
                    module(
                        testConfig(UNREACHABLE_DB),
                        eagerDatabase = false,
                        startBackgroundServices = false,
                    )
                }
                val ready = client.get("/health/ready")
                ready.status shouldBe HttpStatusCode.ServiceUnavailable
                ready.bodyAsText() shouldBe """{"status":"not ready"}"""
                client.get("/health/live").status shouldBe HttpStatusCode.OK
            }
        }
    })

private val UNREACHABLE_DB =
    DbConfig(url = "jdbc:postgresql://localhost:1/unused", user = "unused", password = "unused")
