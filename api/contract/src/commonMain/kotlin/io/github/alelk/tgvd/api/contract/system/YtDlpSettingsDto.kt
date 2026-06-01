package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class YtDlpSettingsDto(
    // ── Cookies ──────────────────────────────────────────────────────────────
    /** Browser name for --cookies-from-browser (e.g. "chrome", "firefox"). */
    val cookiesFromBrowser: String? = null,
    /** Text content of a Netscape-format cookies file — saved to a managed temp file on the server. */
    val cookiesContent: String? = null,
    /** Path to a Netscape-format cookies file on the server for --cookies. */
    val cookiesFile: String? = null,

    // ── SSL workarounds ───────────────────────────────────────────────────────
    /** Enable --legacy-server-connect to fix SSL errors on some sites (e.g. RuTube). */
    val legacyServerConnect: Boolean = false,
    /** Enable --no-check-certificate (disables TLS validation — use with caution). */
    val noCheckCertificate: Boolean = false,

    // ── Format / quality ─────────────────────────────────────────────────────
    /** Raw yt-dlp format selector (-f), e.g. "bestvideo[height<=1080]+bestaudio/best". Overrides auto-selection. */
    val preferredFormats: String? = null,
    /** Raw yt-dlp format sort string (-S), e.g. "res,tbr,fps". Used only when preferredFormats is null. */
    val formatSort: String? = null,
    /** Pass --check-formats before downloading (may drop some formats on slow connections). */
    val checkFormats: Boolean = true,
    /** Container for muxing, e.g. "mkv", "mp4", "webm". Null = let yt-dlp decide. */
    val mergeOutputFormat: String? = null,

    // ── Rate limiting / anti-ban ──────────────────────────────────────────────
    /** --rate-limit, e.g. "5M", "500K". Null = unlimited. */
    val rateLimit: String? = null,
    /** --sleep-interval in seconds between requests. */
    val sleepInterval: Int? = null,
    /** --max-sleep-interval in seconds (used together with sleepInterval). */
    val maxSleepInterval: Int? = null,

    // ── Subtitles ─────────────────────────────────────────────────────────────
    /** Download subtitle files alongside the video. */
    val writeSubs: Boolean = false,
    /** Download auto-generated subtitles (e.g. YouTube auto-captions). */
    val writeAutoSubs: Boolean = false,
    /** Comma-separated subtitle language codes, e.g. "ru,en". */
    val subLangs: String? = null,
    /** Embed subtitles into the video file (requires ffmpeg). */
    val embedSubs: Boolean = false,

    // ── Performance ───────────────────────────────────────────────────────────
    /** Number of parallel fragment downloads (--concurrent-fragments). */
    val concurrentFragments: Int = 5,
    /** Socket timeout in seconds (--socket-timeout). */
    val socketTimeout: Int = 30,

    // ── Site-specific ─────────────────────────────────────────────────────────
    /**
     * YouTube player client for --extractor-args "youtube:player_client=VALUE".
     * "ios" and "android" do NOT require a JavaScript runtime (deno/node).
     * "web" gives the most formats but requires deno.
     * Empty string = yt-dlp default (currently "web", requires deno).
     * Ignored if [extractorArgs] already contains "player_client".
     */
    val youtubePlayerClient: String = "ios",
    /**
     * Raw --extractor-args value, e.g. "vk:nocheckcertificate=1".
     * If this contains "player_client", it takes priority over [youtubePlayerClient].
     * Multiple extractors: "youtube:player_client=web;vk:nocheckcertificate=1".
     */
    val extractorArgs: String? = null,
    /** Comma-separated SponsorBlock categories to remove, e.g. "sponsor,selfpromo". */
    val sponsorBlockRemove: String? = null,
    /** Custom User-Agent string (--user-agent). */
    val userAgent: String? = null,

    // ── Per-extractor overrides ───────────────────────────────────────────────
    /**
     * Per-extractor setting overrides. Keyed by yt-dlp extractor name in lowercase
     * (e.g. `"rutube"`, `"youtube"`). Values override global settings for matching URLs.
     */
    val extractorOverrides: Map<String, YtDlpExtractorOverrideDto> = emptyMap(),
)