package io.github.alelk.tgvd.server

import io.github.alelk.tgvd.server.config.InvalidConfigException
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.github.alelk.tgvd.server.route.testConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.application.Application
import io.ktor.server.application.pluginOrNull
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.testing.testApplication
import org.koin.ktor.plugin.Koin

/**
 * Fail-fast start (G2): the database is migrated before Koin and routing are installed, so a broken
 * migration or a config the server must not run with stops `module()` with an exception — and no
 * route is ever mounted. `main()` turns that exception into exit code 1.
 */
class StartupTest :
    FunSpec({
        test("a migration that fails makes module() throw before routing is installed") {
            val db = PostgresTestContainer.newDatabaseConfig()
            // A table that conflicts with V1's `workspaces` (no `id` column). With baselineOnMigrate the
            // non-empty schema is baselined at V1, so the failure surfaces in V2 (`channels` references
            // `workspaces(id)`) — either way Flyway cannot bring the schema up.
            PostgresTestContainer.connect(db).use { connection ->
                connection.createStatement().use { it.execute("CREATE TABLE workspaces (slug TEXT)") }
            }

            val atFailure = StateAtFailure()
            shouldThrowAny {
                testApplication {
                    application { atFailure.capture(this) { module(testConfig(db), startBackgroundServices = false) } }
                    startApplication()
                }
            }

            atFailure.captured shouldBe true
            atFailure.routingInstalled shouldBe false
            atFailure.koinInstalled shouldBe false
        }

        test("an invalid config makes module() throw before the database is touched") {
            val config =
                testConfig(PostgresTestContainer.newDatabaseConfig()).let {
                    it.copy(telegram = it.telegram.copy(botToken = "", devMode = false))
                }

            val atFailure = StateAtFailure()
            val e =
                shouldThrow<InvalidConfigException> {
                    testApplication {
                        application { atFailure.capture(this) { module(config, startBackgroundServices = false) } }
                        startApplication()
                    }
                }

            e.message shouldContain "TELEGRAM_BOT_TOKEN"
            atFailure.captured shouldBe true
            atFailure.routingInstalled shouldBe false
            atFailure.koinInstalled shouldBe false
        }
    })

/**
 * What the application looked like at the moment `module()` threw. Checked there, not afterwards:
 * Ktor disposes a failed application, which uninstalls its plugins.
 */
private class StateAtFailure {
    var captured = false
        private set
    var routingInstalled = false
        private set
    var koinInstalled = false
        private set

    @Suppress("TooGenericExceptionCaught") // records the state for any failure, then rethrows it
    fun capture(application: Application, block: Application.() -> Unit) {
        try {
            application.block()
        } catch (e: Throwable) {
            captured = true
            routingInstalled = application.pluginOrNull(RoutingRoot) != null
            koinInstalled = application.pluginOrNull(Koin) != null
            throw e
        }
    }
}
