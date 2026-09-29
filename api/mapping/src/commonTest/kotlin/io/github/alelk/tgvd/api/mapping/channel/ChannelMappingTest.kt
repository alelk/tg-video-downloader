package io.github.alelk.tgvd.api.mapping.channel

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.domain.channel.ChannelFilter
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Channel bodies and query filters from the wire: never an exception, broken input is a ValidationError (G10). */
class ChannelMappingTest :
    FunSpec({
        context("parseChannelFilter") {
            test("channelId together with extractor wins; then tag; otherwise all") {
                parseChannelFilter("UC-1", "youtube", "music") shouldBe
                    ChannelFilter.ByPlatformId(ChannelId("UC-1"), Extractor("youtube")).right()
                parseChannelFilter("UC-1", null, "music") shouldBe ChannelFilter.ByTag(Tag("music")).right()
                parseChannelFilter(null, "youtube", null) shouldBe ChannelFilter.All.right()
                parseChannelFilter(null, null, null) shouldBe ChannelFilter.All.right()
            }

            test("blank ids and a malformed tag are ValidationErrors") {
                parseChannelFilter(" ", "youtube", null) shouldBe
                    DomainError.ValidationError("channelId", "ChannelId cannot be blank").left()
                parseChannelFilter("UC-1", "", null) shouldBe
                    DomainError.ValidationError("extractor", "Extractor cannot be blank").left()
                parseChannelFilter(null, null, "Bad Tag") shouldBe
                    DomainError.ValidationError(
                        "tag",
                        "Tag must be lowercase alphanumeric with hyphens: Bad Tag",
                    ).left()
            }
        }

        context("CreateChannelDto.toDomain") {
            val body =
                CreateChannelDto(channelId = "UC-1", extractor = "youtube", name = "Channel", tags = listOf("pop"))

            test("maps the body; the workspace is not part of the request") {
                val request = body.toDomain().shouldBeRight()
                request.channelId shouldBe ChannelId("UC-1")
                request.extractor shouldBe Extractor("youtube")
                request.tags shouldBe setOf(Tag("pop"))
            }

            test("a blank channelId or extractor and a malformed tag are ValidationErrors") {
                body.copy(channelId = " ").toDomain() shouldBe
                    DomainError.ValidationError("channelId", "ChannelId cannot be blank").left()
                body.copy(extractor = " ").toDomain() shouldBe
                    DomainError.ValidationError("extractor", "Extractor cannot be blank").left()
                body.copy(tags = listOf("pop", "")).toDomain() shouldBe
                    DomainError.ValidationError("tags", "Tag cannot be blank").left()
            }
        }

        test("UpdateChannelDto.toDomain: absent tags stay absent; a malformed tag is a ValidationError") {
            UpdateChannelDto(name = "N").toDomain().shouldBeRight().tags shouldBe null
            UpdateChannelDto(tags = listOf("UPPER")).toDomain() shouldBe
                DomainError.ValidationError("tags", "Tag must be lowercase alphanumeric with hyphens: UPPER").left()
        }
    })
