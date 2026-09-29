package io.github.alelk.tgvd.server.config

import io.github.alelk.tgvd.server.infra.config.AppConfig

/** Bot tokens that only ever appear as development defaults. */
private val PLACEHOLDER_BOT_TOKENS = setOf("test-token", "dev-token")

/**
 * Every problem of [config] that must stop the server before it listens, as human-readable messages
 * naming the setting and the environment variable to fix. Empty = the config is usable.
 */
fun validateConfig(config: AppConfig): List<String> = buildList {
    val telegram = config.telegram
    if (!telegram.devMode && (telegram.botToken.isBlank() || telegram.botToken in PLACEHOLDER_BOT_TOKENS)) {
        add(
            "telegram.botToken is empty or a development placeholder (${PLACEHOLDER_BOT_TOKENS.joinToString()}) " +
                "while telegram.devMode is false: set TELEGRAM_BOT_TOKEN to the bot token from @BotFather " +
                "(it signs the Mini App initData)",
        )
    }
}

/** Fails fast with ALL problems of [config] in one message (see [validateConfig]). */
fun requireValidConfig(config: AppConfig) {
    val errors = validateConfig(config)
    if (errors.isNotEmpty()) throw InvalidConfigException(errors)
}

class InvalidConfigException(val errors: List<String>) :
    IllegalStateException(
        "Invalid configuration (${errors.size} problem(s)):\n" + errors.joinToString("\n") { "  - $it" },
    )
