package io.github.alelk.tgvd.server.infra.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.alelk.tgvd.server.infra.config.DbConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

class DatabaseFactory(private val config: DbConfig) {

    fun create(): Database {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            minimumIdle = config.minIdle
            idleTimeout = 60000
            connectionTimeout = 30000
            driverClassName = "org.postgresql.Driver"
        })

        // Run Flyway migrations
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()

        return Database.connect(dataSource)
    }
}
