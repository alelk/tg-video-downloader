package io.github.alelk.tgvd.api.contract.rule

import io.github.alelk.tgvd.api.contract.common.apiJson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RuleMatchDtoTest : FunSpec({

    context("ChannelId") {
        test("serializes with type discriminator") {
            val dto: RuleMatchDto = RuleMatchDto.ChannelId(value = "UC123abc")
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "channel-id",
                    "value": "UC123abc"
                }
            """
        }

        test("deserializes from json") {
            val json = """
                {
                    "type": "channel-id",
                    "value": "UC123abc"
                }
            """
            val dto = apiJson.decodeFromString(RuleMatchDto.serializer(), json)
            dto shouldBe RuleMatchDto.ChannelId(value = "UC123abc")
        }
    }

    context("ChannelName") {
        test("serializes with default ignoreCase") {
            val dto: RuleMatchDto = RuleMatchDto.ChannelName(value = "TOLK")
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "channel-name",
                    "value": "TOLK",
                    "ignoreCase": true
                }
            """
        }

        test("deserializes with ignoreCase=false") {
            val json = """
                {
                    "type": "channel-name",
                    "value": "TOLK",
                    "ignoreCase": false
                }
            """
            val dto = apiJson.decodeFromString(RuleMatchDto.serializer(), json)
            dto shouldBe RuleMatchDto.ChannelName(value = "TOLK", ignoreCase = false)
        }
    }

    context("TitleRegex") {
        test("round-trip") {
            val original: RuleMatchDto = RuleMatchDto.TitleRegex(pattern = ".*Lesson\\s+\\d+.*")
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), original)
            json shouldEqualJson """
                {
                    "type": "title-regex",
                    "pattern": ".*Lesson\\s+\\d+.*"
                }
            """
            val decoded = apiJson.decodeFromString(RuleMatchDto.serializer(), json)
            decoded shouldBe original
        }
    }

    context("UrlRegex") {
        test("round-trip") {
            val original: RuleMatchDto = RuleMatchDto.UrlRegex(pattern = "https://youtube\\.com/.*")
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), original)
            val decoded = apiJson.decodeFromString(RuleMatchDto.serializer(), json)
            decoded shouldBe original
        }
    }

    context("AllOf (composite)") {
        test("serializes nested matches") {
            val dto: RuleMatchDto = RuleMatchDto.AllOf(
                matches = listOf(
                    RuleMatchDto.ChannelName(value = "TOLK"),
                    RuleMatchDto.TitleRegex(pattern = ".*Part \\d+.*"),
                )
            )
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "all-of",
                    "matches": [
                        {
                            "type": "channel-name",
                            "value": "TOLK",
                            "ignoreCase": true
                        },
                        {
                            "type": "title-regex",
                            "pattern": ".*Part \\d+.*"
                        }
                    ]
                }
            """
        }

        test("deserializes nested matches") {
            val json = """
                {
                    "type": "all-of",
                    "matches": [
                        { "type": "channel-id", "value": "UC1" },
                        { "type": "url-regex", "pattern": ".*youtube.*" }
                    ]
                }
            """
            val dto = apiJson.decodeFromString(RuleMatchDto.serializer(), json)
            val allOf = dto.shouldBeInstanceOf<RuleMatchDto.AllOf>()
            allOf.matches.size shouldBe 2
            allOf.matches[0] shouldBe RuleMatchDto.ChannelId("UC1")
            allOf.matches[1] shouldBe RuleMatchDto.UrlRegex(".*youtube.*")
        }
    }

    context("AnyOf (composite)") {
        test("serializes nested matches") {
            val dto: RuleMatchDto = RuleMatchDto.AnyOf(
                matches = listOf(
                    RuleMatchDto.ChannelId("UC1"),
                    RuleMatchDto.ChannelId("UC2"),
                )
            )
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "type": "any-of",
                    "matches": [
                        { "type": "channel-id", "value": "UC1" },
                        { "type": "channel-id", "value": "UC2" }
                    ]
                }
            """
        }
    }

    context("deeply nested") {
        test("AllOf containing AnyOf round-trip") {
            val original: RuleMatchDto = RuleMatchDto.AllOf(
                matches = listOf(
                    RuleMatchDto.AnyOf(
                        matches = listOf(
                            RuleMatchDto.ChannelId("UC1"),
                            RuleMatchDto.ChannelId("UC2"),
                        ),
                    ),
                    RuleMatchDto.TitleRegex(".*test.*"),
                ),
            )
            val json = apiJson.encodeToString(RuleMatchDto.serializer(), original)
            val decoded = apiJson.decodeFromString(RuleMatchDto.serializer(), json)
            decoded shouldBe original
        }
    }
})

