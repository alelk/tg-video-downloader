package io.github.alelk.tgvd.domain.system

/**
 * The server-wide settings an administrator edits at runtime (`/system/settings`): how yt-dlp is
 * called and which proxy it uses. Deployment-only values (binary path, timeouts, update policy) are
 * not part of it — they come from the server configuration.
 */
data class SystemSettings(val ytDlp: YtDlpSettings = YtDlpSettings(), val proxy: ProxySettings = ProxySettings())

/**
 * The editable yt-dlp options. Defaults are the server's defaults.
 *
 * [cookiesContent] is a secret: it is stored and used, but never shown back.
 */
data class YtDlpSettings(
    // Cookies
    val cookiesFromBrowser: String? = null,
    val cookiesContent: String? = null,
    val cookiesFile: String? = null,
    // SSL workarounds
    val legacyServerConnect: Boolean = false,
    val noCheckCertificate: Boolean = false,
    // Format / quality
    val preferredFormats: String? = null,
    val formatSort: String? = null,
    val checkFormats: Boolean = true,
    val preferredAudioLanguages: List<String> = emptyList(),
    val maxAdditionalAudioTracks: Int = 2,
    val originalAudioLanguage: String? = null,
    // Rate limiting
    val rateLimit: String? = null,
    val sleepInterval: Int? = null,
    val maxSleepInterval: Int? = null,
    // Subtitles
    val writeSubs: Boolean = true,
    val writeAutoSubs: Boolean = true,
    val preferredSubtitleLanguages: List<String> = listOf("ru", "en"),
    /** Legacy comma-separated subtitle languages; an update turns it into [preferredSubtitleLanguages]. */
    val subLangs: String? = null,
    val embedSubs: Boolean = false,
    val sleepSubtitles: Int? = 3,
    // Performance
    val concurrentFragments: Int = 5,
    val socketTimeout: Int = 30,
    // Site-specific
    val youtubePlayerClient: String = "ios",
    val extractorArgs: String? = null,
    val sponsorBlockRemove: String? = null,
    val userAgent: String? = null,
    /** Per-extractor overrides, keyed by the lowercase extractor name (`"rutube"`). */
    val extractorOverrides: Map<String, YtDlpExtractorOverride> = emptyMap(),
)

/** Overrides of the global yt-dlp options for one extractor; `null` inherits the global value. */
data class YtDlpExtractorOverride(
    val legacyServerConnect: Boolean? = null,
    val noCheckCertificate: Boolean? = null,
    val proxyEnabled: Boolean? = null,
)

/** The proxy yt-dlp uses. [password] is a secret: it is stored and used, but never shown back. */
data class ProxySettings(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
)

enum class ProxyType { HTTP, SOCKS5 }
