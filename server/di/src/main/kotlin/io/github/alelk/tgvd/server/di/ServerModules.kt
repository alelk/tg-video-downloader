package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.server.infra.config.AppConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module

/**
 * All server Koin modules assembled from [AppConfig] and the already opened (migrated) [database].
 * `null` boots the graph without a database: everything that needs one fails on resolution, and the
 * readiness probe reports "not ready".
 */
fun serverModules(config: AppConfig, database: Database?): List<Module> = listOf(
    configModule(config),
    infraModule(database),
    domainModule(),
    transportModule(),
)
