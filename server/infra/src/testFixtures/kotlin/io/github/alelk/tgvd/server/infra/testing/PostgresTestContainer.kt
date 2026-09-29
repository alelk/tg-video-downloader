package io.github.alelk.tgvd.server.infra.testing

import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.db.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * One PostgreSQL container per test JVM, the same image as the `db` service in
 * `docker-compose.yaml` (`postgres:16-alpine`). Started on first use; Testcontainers (Ryuk) removes
 * it when the JVM exits.
 *
 * Isolation between specs: every spec asks for its own **empty database** ([newDatabaseConfig]) or
 * for an empty database already migrated through the production start path ([newMigratedDatabase]).
 * No spec shares tables with another one, so no TRUNCATE ordering is needed and specs may run in
 * any order.
 */
object PostgresTestContainer {
    const val IMAGE = "postgres:16-alpine"

    private const val POSTGRES_PORT = 5432
    private const val POOL_SIZE = 4
    private const val MIN_IDLE = 1

    private val databaseCounter = AtomicInteger()

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(IMAGE)
            .withDatabaseName("tgvd")
            .withUsername("tgvd")
            .withPassword("tgvd")
            .also { it.start() }
    }

    /** Creates a new empty database (no schema) and returns the [DbConfig] that points to it. */
    fun newDatabaseConfig(): DbConfig {
        val name = "tgvd_test_${databaseCounter.incrementAndGet()}"
        adminConnection().use { connection ->
            connection.createStatement().use { it.execute("CREATE DATABASE $name") }
        }
        return DbConfig(
            url = jdbcUrl(name),
            user = container.username,
            password = container.password,
            poolSize = POOL_SIZE,
            minIdle = MIN_IDLE,
        )
    }

    /**
     * Creates a new database and runs the production start path on it ([DatabaseFactory.open]:
     * Hikari pool + Flyway migrations).
     */
    fun newMigratedDatabase(): MigratedTestDatabase {
        val config = newDatabaseConfig()
        return MigratedTestDatabase(config, DatabaseFactory(config).open().database)
    }

    /**
     * Drops the database of [config], terminating its open connections — the database becomes
     * unreachable for a pool that still points to it.
     */
    fun dropDatabase(config: DbConfig) {
        val name = config.url.substringAfterLast('/')
        adminConnection().use { connection ->
            connection.createStatement().use { it.execute("DROP DATABASE $name WITH (FORCE)") }
        }
    }

    /** A plain JDBC connection to the database of [config] — for raw SQL in tests. */
    fun connect(config: DbConfig): Connection = DriverManager.getConnection(config.url, config.user, config.password)

    private fun adminConnection(): Connection =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    private fun jdbcUrl(databaseName: String): String =
        "jdbc:postgresql://${container.host}:${container.getMappedPort(POSTGRES_PORT)}/$databaseName"
}

/** A migrated per-spec database: its [config] (for raw JDBC) and the Exposed [database]. */
data class MigratedTestDatabase(val config: DbConfig, val database: Database)
