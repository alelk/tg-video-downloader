package io.github.alelk.tgvd.server.transport.error

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/** The message of every `500 INTERNAL_ERROR`: a server fault never shows its detail to the client. */
internal const val INTERNAL_ERROR_MESSAGE = "Internal server error"

/**
 * Exceptions that escaped a route. Expected failures never get here — routes fold `Either`.
 *
 * - Malformed input the framework could not turn into a request (G10): a body that is not valid JSON
 *   for the DTO, a path/query parameter that does not convert (Ktor `Resources`), a missing
 *   parameter → `400 VALIDATION_ERROR`.
 * - Anything else is a server fault → `500 INTERNAL_ERROR`, logged with the correlation id; the
 *   message never carries infrastructure detail.
 */
fun StatusPagesConfig.configureDomainErrorHandling() {
    exception<BadRequestException> { call, cause -> call.respondMalformedInput(cause) }
    exception<ContentTransformationException> { call, cause -> call.respondMalformedInput(cause) }
    exception<ContentConvertException> { call, cause -> call.respondMalformedInput(cause) }
    exception<Throwable> { call, cause ->
        val correlationId = call.correlationIdOrNew()
        logger.error(cause) { "Unhandled exception [correlationId=$correlationId]" }
        call.respond(
            HttpStatusCode.InternalServerError,
            apiError("INTERNAL_ERROR", INTERNAL_ERROR_MESSAGE, correlationId),
        )
    }
}

private suspend fun ApplicationCall.respondMalformedInput(cause: Throwable) {
    val correlationId = correlationIdOrNew()
    logger.debug(cause) {
        "Malformed request ${request.httpMethod.value} ${request.path()} [correlationId=$correlationId]"
    }
    respond(HttpStatusCode.BadRequest, apiError("VALIDATION_ERROR", "Malformed request", correlationId))
}

@OptIn(ExperimentalUuidApi::class)
private fun ApplicationCall.correlationIdOrNew(): String = callId ?: Uuid.random().toString()
