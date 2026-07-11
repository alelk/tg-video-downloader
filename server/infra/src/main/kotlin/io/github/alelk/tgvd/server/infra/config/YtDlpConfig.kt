package io.github.alelk.tgvd.server.infra.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Serializable
data class YtDlpConfig(
    val path: String = "yt-dlp",
    val timeout: Duration = 30.minutes,
    val retries: Int = 5,
    val fragmentRetries: Int = 30,
    val allowUpdate: Boolean = true,
    val updateChannel: String = "stable",
    val autoDownload: Boolean = true,

    // ── Cookies ──────────────────────────────────────────────────────────────
    /** Browser name for --cookies-from-browser (e.g. "chrome", "firefox", "safari", "brave"). */
    val cookiesFromBrowser: String? = null,
    /** Text content of Netscape cookies file — written to a managed temp file on startup/update. */
    val cookiesContent: String? = null,
    /** Path to Netscape-format cookies file for --cookies. */
    val cookiesFile: String? = null,

    // ── SSL ───────────────────────────────────────────────────────────────────
    /**
     * Pass --legacy-server-connect to yt-dlp.
     * Workaround for SSL: UNEXPECTED_EOF_WHILE_READING errors on some sites (e.g. RuTube).
     */
    val legacyServerConnect: Boolean = false,
    /**
     * Pass --no-check-certificate to yt-dlp.
     * Disables TLS certificate validation entirely — use only when you trust the network.
     */
    val noCheckCertificate: Boolean = false,

    // ── Format / quality ─────────────────────────────────────────────────────
    /** Raw yt-dlp format selector (-f). When set, overrides auto-selection based on DownloadPolicy. */
    val preferredFormats: String? = null,
    /** Raw yt-dlp format sort string (-S). Used only when preferredFormats is null. */
    val formatSort: String? = null,
    /** Pass --check-formats before downloading. May drop formats on slow connections. */
    val checkFormats: Boolean = true,
    /** Container for muxing (--merge-output-format), e.g. "mkv", "mp4". Null = yt-dlp default. */
    val mergeOutputFormat: String? = null,

    // ── Rate limiting / anti-ban ──────────────────────────────────────────────
    /** --rate-limit, e.g. "5M", "500K". Null = unlimited. */
    val rateLimit: String? = null,
    /** --sleep-interval between requests in seconds. */
    val sleepInterval: Int? = null,
    /** --max-sleep-interval in seconds (used with sleepInterval). */
    val maxSleepInterval: Int? = null,

    // ── Subtitles ─────────────────────────────────────────────────────────────
    val writeSubs: Boolean = false,
    val writeAutoSubs: Boolean = false,
    /** Comma-separated language codes, e.g. "ru,en". */
    val subLangs: String? = null,
    val embedSubs: Boolean = false,

    // ── Performance ───────────────────────────────────────────────────────────
    /** --concurrent-fragments: parallel fragment downloads. */
    val concurrentFragments: Int = 5,
    /** --socket-timeout in seconds. */
    val socketTimeout: Int = 30,

    // ── Site-specific ─────────────────────────────────────────────────────────
    /**
     * YouTube player client for --extractor-args "youtube:player_client=VALUE".
     * "ios" and "android" do NOT require a JavaScript runtime (deno/node).
     * "web" gives the most formats but requires deno to be installed.
     * Empty string = yt-dlp default (currently "web", requires deno).
     * Ignored if [extractorArgs] already contains "player_client".
     */
    val youtubePlayerClient: String = "ios",
    /** Raw --extractor-args value. If it contains "player_client", takes priority over [youtubePlayerClient]. */
    val extractorArgs: String? = null,
    /** Comma-separated SponsorBlock categories, e.g. "sponsor,selfpromo". */
    val sponsorBlockRemove: String? = null,
    /** Custom --user-agent string. */
    val userAgent: String? = null,

    // ── Per-extractor overrides ───────────────────────────────────────────────
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
@Serializable
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

