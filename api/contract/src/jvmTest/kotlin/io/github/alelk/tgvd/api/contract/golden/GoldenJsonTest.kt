package io.github.alelk.tgvd.api.contract.golden

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * Golden JSON of the wire: what the server sends today for jobs, rules, channels, previews, system
 * settings, workspaces and errors. The files under `src/jvmTest/resources/golden/` were written once
 * (stage 01.4) by `apiJson` from [GoldenCases]; they are the contract old clients already parse.
 *
 * Both directions through `apiJson`: `encode(value)` equals the golden JSON (same keys, same values,
 * no extra or missing keys; key order is free) and `decode(golden)` equals the value. A field
 * rename, a changed `@SerialName`, a new default or a dropped field fails here.
 *
 * Changing a golden file is a wire change (G1: additions only) — never regenerate them to go green.
 */
class GoldenJsonTest :
    FunSpec({
        test("every golden file has a case and every case has a golden file") {
            goldenFilesOnDisk() shouldBe GoldenCases.all.map { "${it.name}.json" }.toSet()
        }

        GoldenCases.all.forEach { case ->
            test("${case.name}: encode(value) == golden") {
                case.encode() shouldEqualJson readGolden(case.name)
            }

            test("${case.name}: decode(golden) == value") {
                case.decode(readGolden(case.name)) shouldBe case.value
            }
        }
    })

private fun <T> GoldenCase<T>.encode(): String = apiJson.encodeToString(serializer, value)

private fun <T> GoldenCase<T>.decode(json: String): T = apiJson.decodeFromString(serializer as KSerializer<T>, json)

private fun readGolden(name: String): String =
    checkNotNull(GoldenJsonTest::class.java.classLoader.getResource("golden/$name.json")) {
        "missing golden/$name.json"
    }.readText()

private fun goldenFilesOnDisk(): Set<String> =
    File(checkNotNull(GoldenJsonTest::class.java.classLoader.getResource("golden")).toURI())
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .map { it.name }
        .toSet()
