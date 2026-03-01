package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.server.infra.config.AppConfig
import org.koin.core.module.Module

/** All server Koin modules assembled from [AppConfig]. */
fun serverModules(config: AppConfig): List<Module> = listOf(
    configModule(config),
    infraModule(),
    domainModule(),
    transportModule(),
)
