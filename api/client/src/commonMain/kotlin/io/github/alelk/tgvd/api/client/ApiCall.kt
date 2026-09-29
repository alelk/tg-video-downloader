package io.github.alelk.tgvd.api.client

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/** Maximum number of body characters quoted in the message of an error response that is not an `ApiErrorDto`. */
private const val UNPARSABLE_BODY_PREVIEW_CHARS = 500

/**
 * The single place where a call becomes a value: the request fails → [ApiError.Network], a non-2xx
 * status → [ApiError.Http], an undecodable 2xx body → [ApiError.Decoding].
 *
 * Cancellation is always rethrown — never `runCatching` around a suspend call, it would swallow it.
 */
internal suspend inline fun <reified T> apiCall(request: () -> HttpResponse): Either<ApiError, T> =
    send(request).flatMap { response ->
        if (response.status.isSuccess()) response.decode<T>() else response.toApiError().left()
    }

/** Same as [apiCall] for endpoints without a response body (`204 No Content`). */
internal suspend inline fun apiCallNoContent(request: () -> HttpResponse): Either<ApiError, Unit> =
    send(request).flatMap { response ->
        if (response.status.isSuccess()) Unit.right() else response.toApiError().left()
    }

/**
 * Any failure to get a response is a transport error. `Throwable`, not `Exception`: the Ktor JS engine
 * reports a failed `fetch` (offline, DNS, CORS) as `kotlin.Error("Fail to fetch")`, which is not an
 * `Exception` — it would escape to the caller and, on the canvas web target, kill the Compose scene.
 */
@Suppress("TooGenericExceptionCaught") // see above; cancellation is rethrown
internal suspend inline fun send(request: () -> HttpResponse): Either<ApiError, HttpResponse> =
    try {
        request().right()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ApiError.Network(e.message, e).left()
    }

@Suppress("TooGenericExceptionCaught") // decoder exception types differ per platform; cancellation is rethrown
internal suspend inline fun <reified T> HttpResponse.decode(): Either<ApiError, T> =
    try {
        body<T>().right()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiError.Decoding(e.message, e).left()
    }

/** Non-2xx response → [ApiError.Http]; a body that is not an `ApiErrorDto` still yields one, not an exception. */
@Suppress("TooGenericExceptionCaught") // see [decode]; cancellation is rethrown
internal suspend fun HttpResponse.toApiError(): ApiError {
    val dto =
        try {
            body<ApiErrorDto>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    return if (dto != null) {
        ApiError.Http(
            status = status.value,
            code = dto.error.code,
            message = dto.error.message,
            correlationId = dto.error.correlationId,
        )
    } else {
        ApiError.Http(
            status = status.value,
            code = "HTTP_${status.value}",
            message = "${status.value} ${status.description}: ${bodyPreview()}",
            correlationId = "",
        )
    }
}

private suspend fun HttpResponse.bodyPreview(): String = try {
    bodyAsText().take(UNPARSABLE_BODY_PREVIEW_CHARS)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    ""
}
