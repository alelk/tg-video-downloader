package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.server.infra.config.*
import org.koin.dsl.module

internal fun configModule(config: AppConfig) = module {
    single { config }
    single { config.server }
    single { config.telegram }
    single { config.db }
    single { config.storage }
    single { config.ytDlp }
    single { config.ffmpeg }
    single { config.postProcess }
    single { config.jobs }
    single { config.logging }
    single { config.llm }
    single { config.proxy }
}

