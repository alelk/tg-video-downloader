package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.github.alelk.tgvd.server.transport.auth.TelegramAuthValidator
import org.koin.dsl.module

internal fun transportModule() = module {
    single {
        val config = get<TelegramConfig>()
        TelegramAuthValidator(
            botToken = config.botToken,
            devMode = config.devMode,
        )
    }
}

