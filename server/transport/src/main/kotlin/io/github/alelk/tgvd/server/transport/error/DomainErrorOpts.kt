package io.github.alelk.tgvd.server.transport.error

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.ktor.http.*


fun DomainError.toHttpResponse(correlationId: String): Pair<HttpStatusCode, ApiErrorDto> = when (this) {
    is DomainError.ValidationError ->
        HttpStatusCode.BadRequest to apiError("VALIDATION_ERROR", message, correlationId)

    is DomainError.InvalidUrl ->
        HttpStatusCode.BadRequest to apiError("INVALID_URL", message, correlationId)

    is DomainError.Unauthorized ->
        HttpStatusCode.Unauthorized to apiError("UNAUTHORIZED", message, correlationId)

    is DomainError.Forbidden ->
        HttpStatusCode.Forbidden to apiError("FORBIDDEN", message, correlationId)

    is DomainError.RuleNotFound ->
        HttpStatusCode.NotFound to apiError("NOT_FOUND", message, correlationId)

    is DomainError.JobNotFound ->
        HttpStatusCode.NotFound to apiError("NOT_FOUND", message, correlationId)

    is DomainError.JobAlreadyExists ->
        HttpStatusCode.Conflict to apiError("CONFLICT", message, correlationId)

    is DomainError.JobCannotBeCancelled ->
        HttpStatusCode.Conflict to apiError("CONFLICT", message, correlationId)

    is DomainError.JobCannotBeRetried ->
        HttpStatusCode.Conflict to apiError("CONFLICT", message, correlationId)

    is DomainError.VideoUnavailable ->
        HttpStatusCode.UnprocessableEntity to apiError("VIDEO_UNAVAILABLE", message, correlationId)

    is DomainError.VideoExtractionFailed ->
        HttpStatusCode.UnprocessableEntity to apiError("VIDEO_UNAVAILABLE", message, correlationId)

    is DomainError.LlmError ->
        HttpStatusCode.BadGateway to apiError("LLM_ERROR", message, correlationId)

    is DomainError.PathTraversalAttempt ->
        HttpStatusCode.BadRequest to apiError("VALIDATION_ERROR", message, correlationId)

    is DomainError.StorageFailed ->
        HttpStatusCode.InternalServerError to apiError("INTERNAL_ERROR", message, correlationId)

    is DomainError.DownloadFailed ->
        HttpStatusCode.InternalServerError to apiError("INTERNAL_ERROR", message, correlationId)

    is DomainError.PostProcessingFailed ->
        HttpStatusCode.InternalServerError to apiError("INTERNAL_ERROR", message, correlationId)

    is DomainError.WorkspaceNotFound ->
        HttpStatusCode.NotFound to apiError("NOT_FOUND", message, correlationId)

    is DomainError.WorkspaceAccessDenied ->
        HttpStatusCode.Forbidden to apiError("FORBIDDEN", message, correlationId)
}



