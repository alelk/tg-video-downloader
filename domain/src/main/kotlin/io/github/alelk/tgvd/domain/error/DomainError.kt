package io.github.alelk.tgvd.domain.error

sealed interface DomainError {
    val message: String
}

sealed interface YtDlpError : DomainError {
    data class ExecutionError(override val message: String, val exitCode: Int? = null) : YtDlpError
    data class VersionParseError(override val message: String) : YtDlpError
    data class UpdateError(override val message: String) : YtDlpError
}
