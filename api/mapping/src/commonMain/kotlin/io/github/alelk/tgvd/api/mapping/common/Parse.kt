package io.github.alelk.tgvd.api.mapping.common

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parses a UUID string from the wire (path parameter or body field) and wraps it with [wrap].
 *
 * @return the id, or [DomainError.ValidationError] for [fieldName] when [raw] is not a UUID.
 */
@OptIn(ExperimentalUuidApi::class)
fun <T> parseId(raw: String, fieldName: String, wrap: (Uuid) -> T): Either<DomainError.ValidationError, T> =
    Uuid.parseOrNull(raw)?.let(wrap)?.right()
        ?: DomainError.ValidationError(fieldName, "Invalid $fieldName: $raw").left()

/**
 * Builds a domain value class from wire input without throwing: a value its `init` block rejects
 * (`require(...)`) becomes [DomainError.ValidationError] for [fieldName] with the rejection message.
 */
fun <T> parseValue(fieldName: String, build: () -> T): Either<DomainError.ValidationError, T> = try {
    build().right()
} catch (e: IllegalArgumentException) {
    DomainError.ValidationError(fieldName, e.message ?: "Invalid $fieldName").left()
}
