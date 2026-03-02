package io.github.alelk.tgvd.api.contract.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ApiErrorDtoTest : FunSpec({

    context("ApiErrorDto") {
        test("serializes error without details") {
            val dto = ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "VALIDATION_ERROR",
                    message = "Invalid workspaceId: default",
                    correlationId = "248c9a73-0256-4b9c-9c53-70d611606197",
                ),
            )
            val json = apiJson.encodeToString(ApiErrorDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "error": {
                        "code": "VALIDATION_ERROR",
                        "message": "Invalid workspaceId: default",
                        "correlationId": "248c9a73-0256-4b9c-9c53-70d611606197",
                        "details": null
                    }
                }
            """
        }

        test("serializes error with json details") {
            val dto = ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "INTERNAL_ERROR",
                    message = "Something went wrong",
                    correlationId = "abc-123",
                    details = buildJsonObject {
                        put("field", JsonPrimitive("url"))
                        put("reason", JsonPrimitive("malformed"))
                    },
                ),
            )
            val json = apiJson.encodeToString(ApiErrorDto.serializer(), dto)
            json shouldEqualJson """
                {
                    "error": {
                        "code": "INTERNAL_ERROR",
                        "message": "Something went wrong",
                        "correlationId": "abc-123",
                        "details": {
                            "field": "url",
                            "reason": "malformed"
                        }
                    }
                }
            """
        }

        test("deserializes from json") {
            val json = """
                {
                    "error": {
                        "code": "UNAUTHORIZED",
                        "message": "Invalid initData: MissingHash",
                        "correlationId": "df916ae7-29fa-45ad-a48a-59672ceadcb5"
                    }
                }
            """
            val dto = apiJson.decodeFromString(ApiErrorDto.serializer(), json)
            dto.error.code shouldBe "UNAUTHORIZED"
            dto.error.message shouldBe "Invalid initData: MissingHash"
            dto.error.details shouldBe null
        }

        test("round-trip") {
            val original = ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "NOT_FOUND",
                    message = "Job not found",
                    correlationId = "test-id",
                ),
            )
            val json = apiJson.encodeToString(ApiErrorDto.serializer(), original)
            val decoded = apiJson.decodeFromString(ApiErrorDto.serializer(), json)
            decoded shouldBe original
        }
    }
})

