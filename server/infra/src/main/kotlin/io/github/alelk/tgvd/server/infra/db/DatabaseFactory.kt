package io.github.alelk.tgvd.server.infra.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.alelk.tgvd.server.infra.config.DbConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

private const val IDLE_TIMEOUT_MS = 60_000L
private const val CONNECTION_TIMEOUT_MS = 30_000L

class DatabaseFactory(private val config: DbConfig) {
    /**
     * The start path of the database: Hikari pool → Flyway `migrate` → Exposed [Database].
     *
     * Called once at server start, before the HTTP routes are installed (fail-fast): a database that
     * cannot be reached or a migration that fails throws here, and the pool opened so far is closed.
     */
    @Suppress("TooGenericExceptionCaught") // close the pool on any failure, then rethrow it unchanged
    fun open(): OpenDatabase {
        val dataSource = HikariDataSource(hikariConfig())
        try {
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate()
            return OpenDatabase(Database.connect(dataSource), dataSource)
        } catch (e: Throwable) {
            dataSource.close()
            throw e
        }
    }

    private fun hikariConfig() = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = config.poolSize
        minimumIdle = config.minIdle
        idleTimeout = IDLE_TIMEOUT_MS
        connectionTimeout = CONNECTION_TIMEOUT_MS
        driverClassName = "org.postgresql.Driver"
    }
}

/** A migrated, connected [database] that owns its connection pool: [close] releases the pool. */
class OpenDatabase(val database: Database, private val dataSource: HikariDataSource) : AutoCloseable {
    override fun close() {
        dataSource.close()
    }
}
