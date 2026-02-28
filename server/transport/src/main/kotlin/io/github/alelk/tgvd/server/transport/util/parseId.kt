package io.github.alelk.tgvd.server.transport.util

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Safely parses a string to [Uuid] and wraps it with [wrap].
 *
 * Returns [Either.Left] with [DomainError.ValidationError] if parsing fails.
 */
@OptIn(ExperimentalUuidApi::class)
inline fun <T> parseId(raw: String, fieldName: String = "id", wrap: (Uuid) -> T): Either<DomainError.ValidationError, T> =
    try {
        wrap(Uuid.parse(raw)).right()
    } catch (_: Exception) {
        DomainError.ValidationError(fieldName, "Invalid $fieldName: $raw").left()
    }


