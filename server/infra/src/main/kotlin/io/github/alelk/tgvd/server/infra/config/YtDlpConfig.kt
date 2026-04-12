package io.github.alelk.tgvd.server.infra.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class YtDlpConfig(
    val path: String = "yt-dlp",
    val timeout: Duration = 30.minutes,
    val retries: Int = 5,
    val fragmentRetries: Int = 30,
    val allowUpdate: Boolean = true,
    val updateChannel: String = "stable",
    val autoDownload: Boolean = true,
    /** Browser name for --cookies-from-browser (e.g. "chrome", "firefox", "safari", "brave") */
    val cookiesFromBrowser: String? = null,
    /** Path to Netscape-format cookies file for --cookies */
    val cookiesFile: String? = null,
    /**
     * Pass --legacy-server-connect to yt-dlp.
     * Workaround for SSL: UNEXPECTED_EOF_WHILE_READING errors on some sites (e.g. RuTube).
     * Allows connecting to servers that don't support RFC 5746 secure renegotiation.
     */
    val legacyServerConnect: Boolean = false,
    /**
     * Pass --no-check-certificate to yt-dlp.
     * Disables TLS certificate validation entirely — use only when you trust the network.
     */
    val noCheckCertificate: Boolean = false,
    /**
     * Per-extractor setting overrides. Key = yt-dlp extractor name in lowercase (e.g. "rutube", "youtube").
     * Settings defined here take precedence over the global values above for matching URLs.
     *
     * Matching: URL is checked against each key — if the URL contains the key as a substring
     * (case-insensitive), the corresponding override is applied.
     *
     * Example (application.yaml):
     * ```yaml
     * ytDlp:
     *   extractorOverrides:
     *     rutube:
     *       legacyServerConnect: true
     *       proxyEnabled: false
     * ```
     */
    val extractorOverrides: Map<String, YtDlpExtractorOverride> = emptyMap(),
)

/**
 * Per-extractor overrides for yt-dlp settings.
 * Each field is nullable — `null` means "inherit from global config".
 */
data class YtDlpExtractorOverride(
    /** Override --legacy-server-connect. null = inherit global. */
    val legacyServerConnect: Boolean? = null,
    /** Override --no-check-certificate. null = inherit global. */
    val noCheckCertificate: Boolean? = null,
    /**
     * Override proxy usage for this extractor.
     * `true`/`false` = force enable/disable proxy regardless of global proxy.enabled.
     * `null` = inherit global proxy.enabled.
     */
    val proxyEnabled: Boolean? = null,
)

