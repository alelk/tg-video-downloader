package io.github.alelk.tgvd.server.config

import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `TELEGRAM_ALLOWED_USER_IDS` / `TELEGRAM_ALLOWED_USERNAMES` → `application.yaml` → [TelegramConfig]
 * through the real [loadConfig] (Fork 5; the mine of stage 01.5: an empty variable is an EMPTY list).
 *
 * The `${VAR:-}` placeholders are resolved by Hoplite's preprocessor from the process environment
 * only (it cannot be pointed at a map), so every case runs [ConfigBindingProbe] in a child JVM with
 * exactly the given environment.
 *
 * To see it fail: drop the normalisation in `loadConfig` (an empty variable binds as `[""]`), or put
 * `allowedUserIds: []` back into `application.yaml` (the variable never reaches the config).
 */
class ConfigBindingTest :
    FunSpec({
        test("unset variables -> empty allow-lists") {
            val bound = loadInChildJvm(emptyMap())
            bound.userIds.shouldBeEmpty()
            bound.usernames.shouldBeEmpty()
        }

        test("empty variables -> empty allow-lists, not a list with one empty string") {
            val bound = loadInChildJvm(mapOf("TELEGRAM_ALLOWED_USER_IDS" to "", "TELEGRAM_ALLOWED_USERNAMES" to ""))
            bound.userIds.shouldBeEmpty()
            bound.usernames.shouldBeEmpty()
        }

        test("comma-separated ids with spaces -> each id, trimmed") {
            loadInChildJvm(mapOf("TELEGRAM_ALLOWED_USER_IDS" to "1, 2")).userIds shouldBe listOf("1", "2")
        }

        test("usernames are bound as written; the auth plugin lower-cases them and strips '@'") {
            val bound = loadInChildJvm(mapOf("TELEGRAM_ALLOWED_USERNAMES" to "@Alice,bob"))
            bound.usernames shouldBe listOf("@Alice", "bob")
        }
    })

private data class BoundAllowLists(val userIds: List<String>, val usernames: List<String>)

private const val CHILD_TIMEOUT_SECONDS = 60L

private fun loadInChildJvm(variables: Map<String, String>): BoundAllowLists {
    val java = File(System.getProperty("java.home"), "bin/java").path
    val process =
        ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), ConfigBindingProbe::class.java.name)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                // No external config file and no profile file: only the committed application.yaml.
                environment()["APP_CONFIG"] = "/nonexistent/tgvd-config-binding-test.yaml"
                environment()["APP_PROFILE"] = "config-binding-test"
                environment().putAll(variables)
            }.start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "config probe did not finish:\n$output" }
    check(process.exitValue() == 0) { "config probe failed (${process.exitValue()}):\n$output" }
    val line =
        checkNotNull(output.lines().firstOrNull { it.startsWith(CONFIG_BINDING_RESULT_MARKER) }) {
            "no result line in the probe output:\n$output"
        }
    val json: JsonObject = Json.parseToJsonElement(line.removePrefix(CONFIG_BINDING_RESULT_MARKER)).jsonObject
    return BoundAllowLists(
        userIds = json.getValue("allowedUserIds").jsonArray.map { it.jsonPrimitive.content },
        usernames = json.getValue("allowedUsernames").jsonArray.map { it.jsonPrimitive.content },
    )
}
