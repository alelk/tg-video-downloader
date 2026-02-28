package io.github.alelk.tgvd.server.transport.util

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.server.transport.error.toHttpResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


/**
 * Responds with an [Either] result:
 * - [Either.Left] → maps [DomainError] to appropriate HTTP status and [ApiErrorDto]
 * - [Either.Right] → responds with [successStatus] and the transformed value
 *
 * Eliminates repetitive `fold(ifLeft = { ... }, ifRight = { ... })` boilerplate in routes.
 */
suspend inline fun <reified T : Any, R> RoutingCall.respondEither(
    result: Either<DomainError, R>,
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    transform: (R) -> T,
) {
    result.fold(
        ifLeft = { error ->
            val (status, body) = error.toHttpResponse(correlationId)
            respond(status, body)
        },
        ifRight = { value ->
            respond(successStatus, transform(value))
        },
    )
}

/**
 * Responds with an [Either] result without transformation.
 * The right value must be directly serializable.
 */
suspend inline fun <reified T : Any> RoutingCall.respondEither(
    result: Either<DomainError, T>,
    successStatus: HttpStatusCode = HttpStatusCode.OK,
) {
    respondEither(result, successStatus) { it }
}

