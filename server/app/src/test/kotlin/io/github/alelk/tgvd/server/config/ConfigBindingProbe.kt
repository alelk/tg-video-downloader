package io.github.alelk.tgvd.server.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Marks the probe's result line on stdout (the rest is the child's log output). */
const val CONFIG_BINDING_RESULT_MARKER = "CONFIG-BINDING-RESULT "

/**
 * Entry point of the child JVM started by [ConfigBindingTest]: loads the config through the real
 * [loadConfig] with the environment the test gave the process, prints the bound Telegram allow-lists.
 */
object ConfigBindingProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val telegram = loadConfig().telegram
        val result =
            JsonObject(
                mapOf(
                    "allowedUserIds" to JsonArray(telegram.allowedUserIds.map(::JsonPrimitive)),
                    "allowedUsernames" to JsonArray(telegram.allowedUsernames.map(::JsonPrimitive)),
                ),
            )
        println(CONFIG_BINDING_RESULT_MARKER + result)
    }
}
