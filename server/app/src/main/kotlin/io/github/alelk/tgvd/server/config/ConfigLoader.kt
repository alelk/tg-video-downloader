package io.github.alelk.tgvd.server.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import io.github.alelk.tgvd.server.infra.config.AppConfig
import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val DEFAULT_PROFILE = "local"
private const val DEFAULT_EXTERNAL_CONFIG = "/app/config/application.yaml"

/**
 * Loads [AppConfig]. Sources, first wins:
 * 1. environment variables ([env]);
 * 2. the external file `APP_CONFIG` (default `/app/config/application.yaml`; optional — in Docker
 *    it is the compose `configs.server-config`);
 * 3. `application-<APP_PROFILE>.yaml` from the classpath (default profile `local`; optional);
 * 4. `application.yaml` from the classpath — the committed defaults.
 *
 * `${VAR:-default}` placeholders inside the YAML files are resolved by Hoplite's preprocessor from the
 * **process** environment (`System.getenv`, then system properties) — Hoplite 2.9 does not let it read
 * another map, so [env] only governs the sources above.
 *
 * The Telegram allow-lists are normalised after binding (see [withNormalizedAllowLists]). The result
 * is not validated here — call [requireValidConfig].
 */
fun loadConfig(env: Map<String, String> = System.getenv()): AppConfig {
    val profile = env["APP_PROFILE"] ?: DEFAULT_PROFILE
    val externalConfig = env["APP_CONFIG"] ?: DEFAULT_EXTERNAL_CONFIG
    logger.info { "Loading configuration with profile: $profile, external config: $externalConfig" }

    return ConfigLoaderBuilder
        .default()
        .addPropertySource(
            EnvironmentVariablesPropertySource(
                useUnderscoresAsSeparator = true,
                allowUppercaseNames = true,
                environmentVariableMap = { env },
            ),
        ).addFileSource(externalConfig, optional = true)
        .addResourceSource("/application-$profile.yaml", optional = true)
        .addResourceSource("/application.yaml")
        .build()
        .loadConfigOrThrow<AppConfig>()
        .let { it.copy(telegram = it.telegram.withNormalizedAllowLists()) }
}

/**
 * `TELEGRAM_ALLOWED_USER_IDS` / `TELEGRAM_ALLOWED_USERNAMES` arrive as one comma-separated string:
 * every entry is trimmed and blank entries are dropped. An unset or empty variable is an **empty**
 * list — never a list with one empty string, which would make the allow-list non-empty and lock
 * every user out with `403`.
 */
fun TelegramConfig.withNormalizedAllowLists(): TelegramConfig = copy(
    allowedUserIds = normalizeAllowList(allowedUserIds),
    allowedUsernames = normalizeAllowList(allowedUsernames),
)

internal fun normalizeAllowList(values: List<String>): List<String> = values
    .flatMap { it.split(',') }
    .map { it.trim() }
    .filter { it.isNotEmpty() }
