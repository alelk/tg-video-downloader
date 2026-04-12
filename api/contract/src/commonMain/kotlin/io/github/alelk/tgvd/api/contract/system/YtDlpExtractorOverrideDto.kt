package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

/**
 * Per-extractor overrides for yt-dlp settings.
 *
 * Key in [YtDlpSettingsDto.extractorOverrides] = yt-dlp extractor name in lowercase
 * (e.g. `"rutube"`, `"youtube"`, `"vk"`).
 *
 * Each field is nullable — `null` means "inherit from global config".
 *
 * Example:
 * ```json
 * {
 *   "ytDlp": {
 *     "extractorOverrides": {
 *       "rutube": { "legacyServerConnect": true, "proxyEnabled": false }
 *     }
 *   }
 * }
 * ```
 */
@Serializable
data class YtDlpExtractorOverrideDto(
    /** Override --legacy-server-connect. null = inherit global. */
    val legacyServerConnect: Boolean? = null,
    /** Override --no-check-certificate. null = inherit global. */
    val noCheckCertificate: Boolean? = null,
    /**
     * Override proxy usage for this extractor.
     * `false` = disable proxy even if globally enabled (e.g. for sites accessible without proxy).
     * `true`  = force proxy even if globally disabled.
     * `null`  = inherit global proxy.enabled.
     */
    val proxyEnabled: Boolean? = null,
)

