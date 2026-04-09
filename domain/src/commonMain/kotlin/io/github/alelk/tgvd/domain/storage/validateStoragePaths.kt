package io.github.alelk.tgvd.domain.storage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError

private val UNSAFE_FILENAME_CHARS = "[:*?\"<>|]".toRegex()

/**
 * Validates that none of the given paths contain path traversal sequences (`..`)
 * or characters that are unsafe in file name segments.
 *
 * Directory separators (`/` and `\`) are allowed in the full path string and are used
 * only to split it into individual segments that are checked separately.
 *
 * @param paths map of field-name → path-value, used for error messages.
 */
fun validateStoragePaths(paths: Map<String, String>): Either<DomainError.ValidationError, Unit> {
    for ((field, path) in paths) {
        if (path.contains("..")) {
            return DomainError.ValidationError(
                field, "Path traversal ('..') is not allowed in '$field'"
            ).left()
        }
        val segments = path.split("/", "\\").filter { it.isNotBlank() }
        for (segment in segments) {
            val found = UNSAFE_FILENAME_CHARS.find(segment)?.value
            if (found != null) {
                return DomainError.ValidationError(
                    field,
                    "Path segment '$segment' in '$field' contains forbidden character '$found'"
                ).left()
            }
        }
    }
    return Unit.right()
}

