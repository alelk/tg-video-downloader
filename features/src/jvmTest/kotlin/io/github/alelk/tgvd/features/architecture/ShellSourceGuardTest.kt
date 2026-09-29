package io.github.alelk.tgvd.features.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * `tgminiapp` is a thin shell (G12, `compose-multiplatform-ui` shell pattern): `main`, Koin, Telegram/JS
 * interop and platform implementations only. Every screen, component and piece of UI state lives in
 * `features`, which the shell renders with a single root call (`TgvdApp`).
 *
 * A `@Composable` declaration in the shell is UI written in the wrong module; so is a Compose
 * foundation/material import (layouts, widgets). The root call itself needs neither — it is a
 * composable lambda passed to `telegramWebApp`.
 *
 * Each rule compares the offending files with its `KNOWN_*` list by exact equality: the lists are
 * empty and may only stay empty.
 */
class ShellSourceGuardTest :
    FunSpec({
        val root = File(SHELL_SOURCE_ROOT)

        test("the scanned shell source root is where the test thinks it is") {
            root.isDirectory shouldBe true
            root.ktFiles().size shouldBeGreaterThan 0
            root.ktFiles().any { it.name == "Main.kt" && it.readText().contains("fun main()") } shouldBe true
        }

        test("no @Composable declarations in the shell: UI lives in features") {
            val offenders = root.offenders { COMPOSABLE_ANNOTATION.containsMatchIn(it) }
            withClue("Shell files declaring composables:\n${offenders.joinToString("\n")}") {
                offenders shouldBe KNOWN_COMPOSABLE_DECLARATIONS
            }
        }

        test("no Compose layout or widget imports in the shell") {
            val offenders = root.offenders { UI_TOOLKIT_IMPORT.containsMatchIn(it) }
            withClue("Shell files importing Compose foundation/material:\n${offenders.joinToString("\n")}") {
                offenders shouldBe KNOWN_UI_TOOLKIT_IMPORTS
            }
        }
    })

/** Relative to the `features` project directory, the working directory of its JVM tests. */
private const val SHELL_SOURCE_ROOT = "../tgminiapp/src"

private val COMPOSABLE_ANNOTATION = Regex("""@Composable\b""")

private val UI_TOOLKIT_IMPORT = Regex("""^import androidx\.compose\.(foundation|material3?)\.""", RegexOption.MULTILINE)

/** Only shrinks. Empty since stage 01.11. */
private val KNOWN_COMPOSABLE_DECLARATIONS = emptyList<String>()

/** Only shrinks. Empty since stage 01.11. */
private val KNOWN_UI_TOOLKIT_IMPORTS = emptyList<String>()

private fun File.ktFiles(): List<File> = walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

private fun File.offenders(hit: (String) -> Boolean): List<String> =
    ktFiles().filter { hit(it.readText()) }.map { it.relativeTo(this).invariantSeparatorsPath }.sorted()
