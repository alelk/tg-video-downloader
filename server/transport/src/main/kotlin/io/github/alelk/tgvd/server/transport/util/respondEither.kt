package io.github.alelk.tgvd.server.transport.util

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.server.transport.error.toHttpResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall

private val logger = KotlinLogging.logger {}

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
        ifLeft = { error -> respondDomainError(error) },
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

/**
 * Responds with the HTTP form of [error]. A [DomainError.DatabaseFailed] is a server fault: it is logged
 * with the correlation id (as `StatusPages` logs an unhandled exception), its detail never reaches the client.
 */
@PublishedApi
internal suspend fun RoutingCall.respondDomainError(error: DomainError) {
    if (error is DomainError.DatabaseFailed) {
        logger.error { "Database failure [correlationId=$correlationId]: ${error.detail}" }
    }
    val (status, body) = error.toHttpResponse(correlationId)
    respond(status, body)
}
