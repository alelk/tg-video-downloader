package io.github.alelk.tgvd.api.client

/**
 * Every way a [TgVideoDownloaderClient] call can fail, as a value (`Either<ApiError, T>`).
 *
 * [message] is the text the UI shows today: the server's message for [Http], the underlying
 * exception's message for [Network] and [Decoding] (it may be `null`; screens fall back to their own
 * text then).
 */
sealed interface ApiError {
    val message: String?

    /**
     * The server answered with a non-2xx status. [code], [message] and [correlationId] come from the
     * `ApiErrorDto` body; when the body is not an `ApiErrorDto`, [code] is `HTTP_<status>`, [message]
     * describes the status and the start of the body, and [correlationId] is empty.
     */
    data class Http(val status: Int, val code: String, override val message: String, val correlationId: String) :
        ApiError

    /** No response: the request could not be sent or the connection failed (offline, timeout, CORS…). */
    data class Network(override val message: String?, val cause: Throwable) : ApiError

    /** A 2xx response whose body could not be decoded into the expected DTO. */
    data class Decoding(override val message: String?, val cause: Throwable) : ApiError
}
