package io.github.alelk.tgvd.api.client

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto

/**
 * Exception thrown when the API returns an error response.
 *
 * Contains the parsed [ApiErrorDto] so callers can access
 * the error code, message, and details.
 */
class ApiException(
    val error: ApiErrorDto,
    val httpStatusCode: Int,
) : Exception(error.error.message) {
    val code: String get() = error.error.code
    val correlationId: String get() = error.error.correlationId
}

