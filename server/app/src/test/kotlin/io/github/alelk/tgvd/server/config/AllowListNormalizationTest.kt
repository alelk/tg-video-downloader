package io.github.alelk.tgvd.server.config

import io.github.alelk.tgvd.server.infra.config.TelegramConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

/** The mine of stage 01.5: an empty allow-list variable must stay an EMPTY list. */
class AllowListNormalizationTest :
    FunSpec({
        context("entries are trimmed, blank entries dropped, one comma-separated string is split") {
            withData(
                nameFn = { (input, expected) -> "$input -> $expected" },
                emptyList<String>() to emptyList(),
                listOf("") to emptyList(),
                listOf("  ") to emptyList(),
                listOf(" , ,") to emptyList(),
                listOf("1, 2") to listOf("1", "2"),
                listOf("1", " 2") to listOf("1", "2"),
                listOf("1,,2,") to listOf("1", "2"),
                listOf("@Alice,bob") to listOf("@Alice", "bob"),
            ) { (input, expected) ->
                normalizeAllowList(input) shouldBe expected
            }
        }

        test("withNormalizedAllowLists normalises both lists") {
            val normalized =
                TelegramConfig(botToken = "t", allowedUserIds = listOf(""), allowedUsernames = listOf(" @Alice , bob "))
                    .withNormalizedAllowLists()
            normalized.allowedUserIds shouldBe emptyList()
            normalized.allowedUsernames shouldBe listOf("@Alice", "bob")
        }
    })
