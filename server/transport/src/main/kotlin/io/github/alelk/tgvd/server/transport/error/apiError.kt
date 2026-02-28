package io.github.alelk.tgvd.server.transport.error

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto

internal fun apiError(code: String, message: String, correlationId: String) =
    ApiErrorDto(
        error = ApiErrorDto.ErrorDetail(
            code = code,
            message = message,
            correlationId = correlationId,
        )
    )