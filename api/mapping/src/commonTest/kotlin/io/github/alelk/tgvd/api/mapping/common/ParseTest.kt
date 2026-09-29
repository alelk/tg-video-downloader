package io.github.alelk.tgvd.api.mapping.common

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.api.mapping.job.parseJobId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.VideoId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ParseTest :
    FunSpec({
        test("parseId wraps a UUID") {
            val uuid = Uuid.random()
            parseJobId(uuid.toString()) shouldBe JobId(uuid).right()
        }

        test("parseId turns a broken UUID into a ValidationError of the field, never an exception") {
            parseJobId("not-a-uuid") shouldBe DomainError.ValidationError("jobId", "Invalid jobId: not-a-uuid").left()
            parseId("", "ruleId") { it } shouldBe DomainError.ValidationError("ruleId", "Invalid ruleId: ").left()
        }

        test("parseValue turns a value-class rejection into a ValidationError with its message") {
            parseValue("source.videoId") { VideoId(" ") } shouldBe
                DomainError.ValidationError("source.videoId", "VideoId cannot be blank").left()
            parseValue("source.videoId") { VideoId("abc") } shouldBe VideoId("abc").right()
        }
    })
