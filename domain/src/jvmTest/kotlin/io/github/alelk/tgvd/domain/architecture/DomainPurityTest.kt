package io.github.alelk.tgvd.domain.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * `domain` is pure Kotlin: it knows only the standard library, Arrow and coroutines
 * (`kotlin-clean-architecture`, AGENTS.md "Rules"). A framework type in `commonMain` ties business
 * rules to one adapter and breaks the JS target; reading `Clock.System` makes time untestable —
 * the clock is injected (G9).
 *
 * Each rule compares the offending files with its `KNOWN_*` list by exact equality: the lists are
 * empty and may only stay empty.
 */
class DomainPurityTest :
    FunSpec({
        val root = File(DOMAIN_SOURCE_ROOT)

        test("the scanned source root is where the test thinks it is") {
            root.isDirectory shouldBe true
            root.ktFiles().size shouldBeGreaterThan 0
        }

        FORBIDDEN_PACKAGES.forEach { forbidden ->
            test("no $forbidden in domain/commonMain") {
                val pattern = Regex("""(^|[^\w.])${Regex.escape(forbidden)}\.""")
                val offenders = root.offenders { pattern.containsMatchIn(it) }
                withClue("Files referring to $forbidden:\n${offenders.joinToString("\n")}") {
                    offenders shouldBe KNOWN_FRAMEWORK_REFERENCES
                }
            }
        }

        test("no Clock.System in domain/commonMain: the clock is injected") {
            val offenders = root.offenders { CLOCK_SYSTEM.containsMatchIn(it) }
            withClue("Files reading Clock.System:\n${offenders.joinToString("\n")}") {
                offenders shouldBe KNOWN_CLOCK_SYSTEM_READS
            }
        }
    })

private const val DOMAIN_SOURCE_ROOT = "src/commonMain/kotlin"

private val FORBIDDEN_PACKAGES = listOf("io.ktor", "org.jetbrains.exposed", "kotlinx.serialization", "org.koin")

private val CLOCK_SYSTEM = Regex("""\bClock\.System\b""")

/** Only shrinks. Empty since stage 01.7. */
private val KNOWN_FRAMEWORK_REFERENCES = emptyList<String>()

/** Only shrinks. Empty since stage 01.7. */
private val KNOWN_CLOCK_SYSTEM_READS = emptyList<String>()

private fun File.ktFiles(): List<File> = walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

private fun File.offenders(hit: (String) -> Boolean): List<String> =
    ktFiles().filter { hit(it.readText()) }.map { it.relativeTo(this).invariantSeparatorsPath }.sorted()
