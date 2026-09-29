package io.github.alelk.tgvd.features.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * UI conventions of `features` (G12, `compose-multiplatform-ui`): a screen talks to the API through its
 * state holder (a Voyager `ScreenModel` built by Koin), never by pulling the client into a composable.
 *
 * The rule compares the offending files with its `KNOWN_*` list by EXACT equality: the test fails on a new
 * violation and on a fixed one still listed. The list only shrinks.
 */
class UiConventionsTest :
    FunSpec({
        val root = File(UI_SOURCE_ROOT)

        // Guard against a vacuous pass: a wrong working directory would make the check below green
        // while checking nothing.
        test("the scanned UI source root is where the test thinks it is") {
            root.isDirectory shouldBe true
            root.ktFiles().size shouldBeGreaterThan 0
            root.ktFiles().any { it.name == "PreviewScreenModel.kt" } shouldBe true
        }

        test("screens do not inject the API client directly: it belongs to a ScreenModel") {
            val offenders = root.offenders { DIRECT_CLIENT_INJECTION.containsMatchIn(it) }
            withClue("Files calling koinInject<TgVideoDownloaderClient>():\n${offenders.joinToString("\n")}") {
                offenders shouldBe KNOWN_DIRECT_CLIENT_SCREENS
            }
        }
    })

/** Relative to the `features` project directory, the working directory of its JVM tests. */
private const val UI_SOURCE_ROOT = "src/commonMain/kotlin/io/github/alelk/tgvd/features"

private val DIRECT_CLIENT_INJECTION = Regex("""koinInject<\s*TgVideoDownloaderClient\s*>""")

/**
 * Screens still calling the client from composition (stage 01.12 moved Preview and Settings to screen models).
 * Only shrinks: move a screen to a `ScreenModel`, then delete its line here.
 */
private val KNOWN_DIRECT_CLIENT_SCREENS =
    listOf(
        "app/WorkspaceGate.kt",
        "channels/screen/ChannelEditorScreen.kt",
        "channels/screen/ChannelListScreen.kt",
        "download/screen/UrlInputScreen.kt",
        "jobs/screen/JobListScreen.kt",
        "navigation/AppNavigation.kt",
        "rules/screen/RuleEditorScreen.kt",
        "rules/screen/RuleListScreen.kt",
    )

private fun File.ktFiles(): List<File> = walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

private fun File.offenders(hit: (String) -> Boolean): List<String> =
    ktFiles().filter { hit(it.readText()) }.map { it.relativeTo(this).invariantSeparatorsPath }.sorted()
