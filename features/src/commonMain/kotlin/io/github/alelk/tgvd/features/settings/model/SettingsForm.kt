package io.github.alelk.tgvd.features.settings.model

import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto

/** Which source of cookies is active in the form; only that one is sent to the server. */
enum class CookiesSource { BROWSER, TEXT, FILE }

/**
 * The settings screen's editable fields, as the person types them (numbers are text until sent).
 * The defaults are what the screen shows before the server's settings arrive.
 */
data class SettingsForm(
    // Cookies
    val cookiesSource: CookiesSource = CookiesSource.BROWSER,
    val cookiesFromBrowser: String = "",
    val cookiesContent: String = "",
    val cookiesFile: String = "",
    // Proxy
    val proxyEnabled: Boolean = false,
    val proxyType: String = "HTTP",
    val proxyHost: String = "127.0.0.1",
    val proxyPort: String = "8080",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    // SSL
    val legacyServerConnect: Boolean = false,
    val noCheckCertificate: Boolean = false,
    // Formats
    val preferredFormats: String = "",
    val formatSort: String = "",
    val checkFormats: Boolean = true,
    val preferredAudioLanguages: String = "",
    val maxAdditionalAudioTracks: String = "2",
    val originalAudioLanguage: String = "",
    // Rate limiting
    val rateLimit: String = "",
    val sleepInterval: String = "",
    val maxSleepInterval: String = "",
    // Subtitles
    val writeSubs: Boolean = false,
    val writeAutoSubs: Boolean = false,
    val subLangs: String = "",
    val embedSubs: Boolean = false,
    val sleepSubtitles: String = "",
    // Advanced
    val concurrentFragments: String = "5",
    val socketTimeout: String = "30",
    val youtubePlayerClient: String = "ios",
    val extractorArgs: String = "",
    val sponsorBlockRemove: String = "",
    val userAgent: String = "",
) {
    /** Any subtitle setting differs from "off" — the section is expanded after loading. */
    val hasSubtitleSettings: Boolean
        get() = writeSubs || writeAutoSubs || subLangs.isNotBlank() || embedSubs || sleepSubtitles.isNotBlank()

    /** Any advanced setting differs from its default — the section is expanded after loading. */
    val hasAdvancedSettings: Boolean
        get() = listOf(rateLimit, sleepInterval, maxSleepInterval, extractorArgs, sponsorBlockRemove, userAgent)
            .any { it.isNotBlank() } ||
            (concurrentFragments.toIntOrNull() ?: DEFAULT_CONCURRENT_FRAGMENTS) != DEFAULT_CONCURRENT_FRAGMENTS ||
            (socketTimeout.toIntOrNull() ?: DEFAULT_SOCKET_TIMEOUT) != DEFAULT_SOCKET_TIMEOUT ||
            youtubePlayerClient != DEFAULT_PLAYER_CLIENT

    /** The request body for `PUT /settings`. */
    fun toRequest(): SystemSettingsDto = SystemSettingsDto(ytDlp = toYtDlpSettings(), proxy = toProxySettings())

    private fun toYtDlpSettings() = YtDlpSettingsDto(
        // Cookies — send only the active source
        cookiesFromBrowser = activeCookies(CookiesSource.BROWSER, cookiesFromBrowser),
        cookiesContent = activeCookies(CookiesSource.TEXT, cookiesContent),
        cookiesFile = activeCookies(CookiesSource.FILE, cookiesFile),
        legacyServerConnect = legacyServerConnect,
        noCheckCertificate = noCheckCertificate,
        preferredFormats = preferredFormats.ifBlankNull(),
        formatSort = formatSort.ifBlankNull(),
        checkFormats = checkFormats,
        preferredAudioLanguages = preferredAudioLanguages.split(',')
            .map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        maxAdditionalAudioTracks = (maxAdditionalAudioTracks.toIntOrNull() ?: DEFAULT_ADDITIONAL_AUDIO_TRACKS)
            .coerceIn(0, MAX_ADDITIONAL_AUDIO_TRACKS),
        originalAudioLanguage = originalAudioLanguage.ifBlankNull(),
        rateLimit = rateLimit.ifBlankNull(),
        sleepInterval = sleepInterval.toIntOrNull(),
        maxSleepInterval = maxSleepInterval.toIntOrNull(),
        writeSubs = writeSubs,
        writeAutoSubs = writeAutoSubs,
        preferredSubtitleLanguages = subLangs.split(',')
            .map { it.trim().replace('_', '-').lowercase() }
            .filter { it.isNotBlank() }
            .distinct(),
        subLangs = null,
        embedSubs = embedSubs,
        sleepSubtitles = sleepSubtitles.toIntOrNull(),
        concurrentFragments = concurrentFragments.toIntOrNull() ?: DEFAULT_CONCURRENT_FRAGMENTS,
        socketTimeout = socketTimeout.toIntOrNull() ?: DEFAULT_SOCKET_TIMEOUT,
        youtubePlayerClient = youtubePlayerClient,
        extractorArgs = extractorArgs.ifBlankNull(),
        sponsorBlockRemove = sponsorBlockRemove.ifBlankNull(),
        userAgent = userAgent.ifBlankNull(),
    )

    private fun toProxySettings() = ProxySettingsDto(
        enabled = proxyEnabled,
        type = proxyType,
        host = proxyHost,
        port = proxyPort.toIntOrNull() ?: DEFAULT_PROXY_PORT,
        username = proxyUsername.ifBlankNull(),
        password = proxyPassword.ifBlankNull(),
    )

    private fun activeCookies(source: CookiesSource, value: String): String? =
        if (cookiesSource == source) value.ifBlankNull() else null

    companion object {
        private const val DEFAULT_CONCURRENT_FRAGMENTS = 5
        private const val DEFAULT_SOCKET_TIMEOUT = 30
        private const val DEFAULT_PLAYER_CLIENT = "ios"
        private const val DEFAULT_ADDITIONAL_AUDIO_TRACKS = 2
        private const val MAX_ADDITIONAL_AUDIO_TRACKS = 8
        private const val DEFAULT_PROXY_PORT = 8080

        /** The form for the server's settings. The proxy password is never sent back (masked on the server). */
        fun from(settings: SystemSettingsDto): SettingsForm {
            val ytDlp = settings.ytDlp
            val proxy = settings.proxy
            return SettingsForm(
                cookiesSource = when {
                    ytDlp.cookiesFromBrowser?.isNotBlank() == true -> CookiesSource.BROWSER
                    ytDlp.cookiesFile?.isNotBlank() == true -> CookiesSource.FILE
                    else -> CookiesSource.BROWSER
                },
                cookiesFromBrowser = ytDlp.cookiesFromBrowser.orEmpty(),
                cookiesContent = ytDlp.cookiesContent.orEmpty(),
                cookiesFile = ytDlp.cookiesFile.orEmpty(),
                proxyEnabled = proxy.enabled,
                proxyType = proxy.type,
                proxyHost = proxy.host,
                proxyPort = proxy.port.toString(),
                proxyUsername = proxy.username.orEmpty(),
                proxyPassword = "",
                legacyServerConnect = ytDlp.legacyServerConnect,
                noCheckCertificate = ytDlp.noCheckCertificate,
                preferredFormats = ytDlp.preferredFormats.orEmpty(),
                formatSort = ytDlp.formatSort.orEmpty(),
                checkFormats = ytDlp.checkFormats,
                preferredAudioLanguages = ytDlp.preferredAudioLanguages.joinToString(", "),
                maxAdditionalAudioTracks = ytDlp.maxAdditionalAudioTracks.toString(),
                originalAudioLanguage = ytDlp.originalAudioLanguage.orEmpty(),
                rateLimit = ytDlp.rateLimit.orEmpty(),
                sleepInterval = ytDlp.sleepInterval?.toString().orEmpty(),
                maxSleepInterval = ytDlp.maxSleepInterval?.toString().orEmpty(),
                writeSubs = ytDlp.writeSubs,
                writeAutoSubs = ytDlp.writeAutoSubs,
                subLangs = ytDlp.preferredSubtitleLanguages.joinToString(", "),
                embedSubs = ytDlp.embedSubs,
                sleepSubtitles = ytDlp.sleepSubtitles?.toString().orEmpty(),
                concurrentFragments = ytDlp.concurrentFragments.toString(),
                socketTimeout = ytDlp.socketTimeout.toString(),
                youtubePlayerClient = ytDlp.youtubePlayerClient,
                extractorArgs = ytDlp.extractorArgs.orEmpty(),
                sponsorBlockRemove = ytDlp.sponsorBlockRemove.orEmpty(),
                userAgent = ytDlp.userAgent.orEmpty(),
            )
        }
    }
}

private fun String.ifBlankNull(): String? = takeIf { it.isNotBlank() }
