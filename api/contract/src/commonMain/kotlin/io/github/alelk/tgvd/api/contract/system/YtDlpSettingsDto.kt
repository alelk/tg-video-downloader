package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class YtDlpSettingsDto(
    val cookiesFromBrowser: String? = null,
    val cookiesFile: String? = null,
    /** Enable --legacy-server-connect to fix SSL errors on some sites (e.g. RuTube). */
    val legacyServerConnect: Boolean = false,
    /** Enable --no-check-certificate (disables TLS validation — use with caution). */
    val noCheckCertificate: Boolean = false,
    /**
     * Per-extractor setting overrides. Keyed by yt-dlp extractor name in lowercase
     * (e.g. `"rutube"`, `"youtube"`). Values override global settings for matching URLs.
     */
    val extractorOverrides: Map<String, YtDlpExtractorOverrideDto> = emptyMap(),
)