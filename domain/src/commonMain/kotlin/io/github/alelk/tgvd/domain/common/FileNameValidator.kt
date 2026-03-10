package io.github.alelk.tgvd.domain.common

/**
 * Validates strings that will be used as file/directory name components.
 *
 * Forbidden characters: / \ : * ? " < > |
 * These are unsafe on all major OS (Windows, Linux, macOS).
 *
 * Path traversal sequences (.., leading dot combinations) are also rejected.
 */
object FileNameValidator {

    /** Regex matching any character that is forbidden in a file/directory name segment. */
    val UNSAFE_CHARS: Regex = "[/\\\\:*?\"<>|]".toRegex()

    /**
     * Returns true when the value is safe to use as a file-name segment.
     * Empty strings are considered invalid.
     */
    fun isSafe(value: String): Boolean =
        value.isNotBlank() && !UNSAFE_CHARS.containsMatchIn(value) && !isPathTraversal(value)

    /**
     * Returns a [DomainError.ValidationError] if [value] contains forbidden characters,
     * or null if the value is safe.
     *
     * @param field  Human-readable field name for the error message.
     * @param value  The value to check.
     * @param allowEmpty When true, blank values are not validated (optional fields).
     */
    fun validate(field: String, value: String, allowEmpty: Boolean = false): DomainError.ValidationError? {
        if (allowEmpty && value.isBlank()) return null
        if (value.isBlank()) return DomainError.ValidationError(field, "$field must not be blank")
        val found = UNSAFE_CHARS.find(value)?.value
        if (found != null) {
            return DomainError.ValidationError(
                field,
                "$field contains forbidden character '$found'. Forbidden: / \\ : * ? \" < > |",
            )
        }
        if (isPathTraversal(value)) {
            return DomainError.ValidationError(field, "$field contains path traversal sequence (..)")
        }
        return null
    }

    /**
     * Sanitize [value] by replacing all unsafe characters with [replacement] (default: underscore).
     * Useful for auto-correcting values (e.g. from yt-dlp titles).
     */
    fun sanitize(value: String, replacement: String = "_"): String =
        value.replace(UNSAFE_CHARS, replacement).trim()

    private fun isPathTraversal(value: String): Boolean =
        value.contains("..") || value.startsWith(".")
}

