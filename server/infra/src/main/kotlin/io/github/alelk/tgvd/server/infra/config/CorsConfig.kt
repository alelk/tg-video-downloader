package io.github.alelk.tgvd.server.infra.config

/**
 * CORS configuration loaded via Hoplite.
 *
 * Safe defaults are tuned for TG Video Downloader:
 * - Telegram Mini App headers (`X-Telegram-Init-Data`, `X-Workspace-Id`)
 * - Correlation header exposed for tracing
 * - `anyHost = true` for local development (override in production)
 */
data class CorsConfig(
    /** Master switch — when false, CORS plugin is not installed. */
    val enabled: Boolean = true,
    /** Allow any origin. Convenient for dev, disable in production. */
    val anyHost: Boolean = false,
    val allowCredentials: Boolean = false,
    /**
     * Allowed origins as `host[:port]` without scheme.
     * Both `http` and `https` are allowed for each entry.
     * Ignored when [anyHost] is `true`.
     */
    val hosts: List<String> = emptyList(),
    /** Request headers the browser is allowed to send. */
    val headers: List<String> = listOf(
        "Content-Type",
        "X-Telegram-Init-Data",
        "X-Workspace-Id",
    ),
    /** Response headers the browser is allowed to read. */
    val exposeHeaders: List<String> = listOf("X-Correlation-Id"),
    /** HTTP methods to allow. */
    val methods: List<String> = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS"),
    /** If true, non-simple content types (e.g. application/json) are allowed. */
    val allowNonSimpleContentTypes: Boolean = true,
)

