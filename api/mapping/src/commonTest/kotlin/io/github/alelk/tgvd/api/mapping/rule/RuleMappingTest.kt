package io.github.alelk.tgvd.api.mapping.rule

import arrow.core.left
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.fixtures.shouldBeLeft
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Rule bodies and ids from the wire: never an exception, broken input is a ValidationError (G10). */
@OptIn(ExperimentalUuidApi::class)
class RuleMappingTest :
    FunSpec({
        val body =
            CreateRuleRequestDto(
                name = "Music",
                match = RuleMatchDto.ChannelId("UC-1"),
                category = CategoryDto.MUSIC_VIDEO,
                metadataTemplate = MetadataTemplateDto.MusicVideo(artistOverride = "Artist"),
                downloadPolicy = DownloadPolicyDto(),
                outputs =
                listOf(OutputRuleDto("/media/{title}.{ext}", OutputFormatDto.OriginalVideo(MediaContainerDto.MKV))),
                priority = 5,
            )

        test("a valid body maps field by field; the workspace is not part of the request") {
            val request = body.toDomainRequest().shouldBeRight()
            request.name shouldBe "Music"
            request.match shouldBe RuleMatch.ChannelId("UC-1")
            request.outputs.single().pathTemplate shouldBe "/media/{title}.{ext}"
            request.priority shouldBe 5
            body.toUpdateDomain().shouldBeRight().match shouldBe RuleMatch.ChannelId("UC-1")
        }

        test("parseRuleId: a UUID, or a ValidationError of ruleId") {
            val uuid = Uuid.random()
            parseRuleId(uuid.toString()).shouldBeRight() shouldBe RuleId(uuid)
            parseRuleId("nope") shouldBe DomainError.ValidationError("ruleId", "Invalid ruleId: nope").left()
        }

        test("an invalid regex is a ValidationError of pattern") {
            listOf(RuleMatchDto.TitleRegex("(unclosed"), RuleMatchDto.UrlRegex("[")).forEach { match ->
                val error = body.copy(match = match).toDomainRequest().shouldBeLeft()
                error.shouldBeInstanceOf<DomainError.ValidationError>().field shouldBe "pattern"
            }
        }

        test("a tag that is not lowercase-with-hyphens is a ValidationError of tag, also nested") {
            val nested = RuleMatchDto.AllOf(listOf(RuleMatchDto.HasTag("ok"), RuleMatchDto.HasTag("Not A Tag")))
            body.copy(match = nested).toUpdateDomain() shouldBe
                DomainError.ValidationError("tag", "Tag must be lowercase alphanumeric with hyphens: Not A Tag").left()
            body.copy(match = RuleMatchDto.HasTag("ok")).toDomainRequest().shouldBeRight().match shouldBe
                RuleMatch.HasTag(Tag("ok"))
        }

        test("a blank output path template is a ValidationError of outputs[i].pathTemplate") {
            val outputs = body.outputs + OutputRuleDto(" ", OutputFormatDto.OriginalVideo(MediaContainerDto.MP4))
            body.copy(outputs = outputs).toDomainRequest() shouldBe
                DomainError.ValidationError("outputs[1].pathTemplate", "Path template must not be blank").left()
        }
    })
