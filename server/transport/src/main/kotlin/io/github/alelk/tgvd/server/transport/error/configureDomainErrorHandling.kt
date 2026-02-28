package io.github.alelk.tgvd.server.transport.error

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
fun StatusPagesConfig.configureDomainErrorHandling() {
    exception<Throwable> { call, cause ->
        val correlationId = call.callId ?: Uuid.random().toString()
        logger.error(cause) { "Unhandled exception [correlationId=$correlationId]" }
        call.respond(
            HttpStatusCode.InternalServerError,
            ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "INTERNAL_ERROR",
                    message = "Internal server error",
                    correlationId = correlationId,
                )
            )
        )
    }
}