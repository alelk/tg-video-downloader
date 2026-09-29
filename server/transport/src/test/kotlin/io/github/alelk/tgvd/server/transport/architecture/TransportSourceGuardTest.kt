package io.github.alelk.tgvd.server.transport.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Transport only translates: parse (`api:mapping`) → use-case → respond (G7). A route that reaches a
 * repository decides business questions (membership, ownership, transactions) outside the use-case;
 * a transport file that imports `server:infra` couples HTTP to one adapter
 * (`kotlin-clean-architecture`: only `server:di` knows ports and adapters together).
 *
 * The Gradle dependency `server:transport → server:infra` is gone since stage 01.7, so an infra
 * import would not even compile; the repository rule is what this test really guards. It matches a
 * repository type by name anywhere in the file, so a wildcard import cannot hide it.
 *
 * Each rule compares the offending files with its `KNOWN_*` list by exact equality: the lists are
 * empty and may only stay empty.
 */
class TransportSourceGuardTest :
    FunSpec({
        val root = File(TRANSPORT_SOURCE_ROOT)

        test("the scanned source root is where the test thinks it is") {
            root.isDirectory shouldBe true
            root.ktFiles().size shouldBeGreaterThan 0
            root.ktFiles().any { it.name == "JobRoutes.kt" } shouldBe true
        }

        test("no repository in transport: routes call use-cases") {
            val offenders = root.offenders { REPOSITORY_TYPE.containsMatchIn(it) }
            withClue("Transport files referring to a repository:\n${offenders.joinToString("\n")}") {
                offenders shouldBe KNOWN_REPOSITORY_REFERENCES
            }
        }

        test("no server.infra in transport") {
            val offenders = root.offenders { INFRA_PACKAGE.containsMatchIn(it) }
            withClue("Transport files referring to server.infra:\n${offenders.joinToString("\n")}") {
                offenders shouldBe KNOWN_INFRA_REFERENCES
            }
        }
    })

private const val TRANSPORT_SOURCE_ROOT = "src/main/kotlin"

/** A domain repository port: `WorkspaceRepository`, `io.github.alelk.tgvd.domain.job.JobRepository`, … */
private val REPOSITORY_TYPE = Regex("""\b[A-Z]\w*Repository\b""")

private val INFRA_PACKAGE = Regex("""\bio\.github\.alelk\.tgvd\.server\.infra\b""")

/** Only shrinks. Empty since stage 01.7. */
private val KNOWN_REPOSITORY_REFERENCES = emptyList<String>()

/** Only shrinks. Empty since stage 01.7. */
private val KNOWN_INFRA_REFERENCES = emptyList<String>()

private fun File.ktFiles(): List<File> = walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

private fun File.offenders(hit: (String) -> Boolean): List<String> =
    ktFiles().filter { hit(it.readText()) }.map { it.relativeTo(this).invariantSeparatorsPath }.sorted()
