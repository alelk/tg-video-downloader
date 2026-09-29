package io.github.alelk.tgvd.server.config

import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.config.ServerConfig
import io.github.alelk.tgvd.server.infra.config.StorageConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ConfigValidationTest :
    FunSpec({
        context("devMode = false refuses an empty or placeholder bot token and names TELEGRAM_BOT_TOKEN") {
            withData(nameFn = { "token '$it'" }, "", "   ", "test-token", "dev-token") { token ->
                val errors = validateConfig(configWith(botToken = token, devMode = false))
                errors shouldHaveSize 1
                errors.single() shouldContain "TELEGRAM_BOT_TOKEN"
                errors.single() shouldContain "telegram.botToken"
            }
        }

        context("devMode = true accepts any bot token") {
            withData(nameFn = { "token '$it'" }, "", "test-token", "dev-token", "123456:real") { token ->
                validateConfig(configWith(botToken = token, devMode = true)).shouldBeEmpty()
            }
        }

        test("devMode = false with a real bot token is valid") {
            validateConfig(configWith(botToken = "123456:ABC-real-token", devMode = false)).shouldBeEmpty()
            shouldNotThrowAny { requireValidConfig(configWith(botToken = "123456:ABC-real-token", devMode = false)) }
        }

        test("requireValidConfig throws with every problem in one message") {
            val invalid = configWith(botToken = "", devMode = false)
            val e = shouldThrow<InvalidConfigException> { requireValidConfig(invalid) }
            e.errors shouldHaveSize 1
            e.message shouldContain "Invalid configuration (1 problem(s))"
            e.message shouldContain "TELEGRAM_BOT_TOKEN"
        }
    })

private fun configWith(botToken: String, devMode: Boolean) = AppConfig(
    server = ServerConfig(),
    telegram = TelegramConfig(botToken = botToken, devMode = devMode),
    db = DbConfig(url = "jdbc:postgresql://localhost:5432/unused", user = "unused", password = "unused"),
    storage = StorageConfig(baseDirectories = listOf("/tmp/tgvd-config-test")),
)
