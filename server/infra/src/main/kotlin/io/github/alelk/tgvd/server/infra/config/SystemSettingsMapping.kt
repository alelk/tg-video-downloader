package io.github.alelk.tgvd.server.infra.config

import io.github.alelk.tgvd.domain.system.ProxySettings
import io.github.alelk.tgvd.domain.system.ProxyType
import io.github.alelk.tgvd.domain.system.YtDlpSettings

/** The runtime-editable part of [YtDlpConfig]. */
fun YtDlpConfig.toYtDlpSettings(): YtDlpSettings = YtDlpSettings(
    cookiesFromBrowser = cookiesFromBrowser,
    cookiesContent = cookiesContent,
    cookiesFile = cookiesFile,
    legacyServerConnect = legacyServerConnect,
    noCheckCertificate = noCheckCertificate,
    preferredFormats = preferredFormats,
    formatSort = formatSort,
    checkFormats = checkFormats,
    preferredAudioLanguages = preferredAudioLanguages,
    maxAdditionalAudioTracks = maxAdditionalAudioTracks,
    originalAudioLanguage = originalAudioLanguage,
    rateLimit = rateLimit,
    sleepInterval = sleepInterval,
    maxSleepInterval = maxSleepInterval,
    writeSubs = writeSubs,
    writeAutoSubs = writeAutoSubs,
    preferredSubtitleLanguages = preferredSubtitleLanguages,
    subLangs = subLangs,
    embedSubs = embedSubs,
    sleepSubtitles = sleepSubtitles,
    concurrentFragments = concurrentFragments,
    socketTimeout = socketTimeout,
    youtubePlayerClient = youtubePlayerClient,
    extractorArgs = extractorArgs,
    sponsorBlockRemove = sponsorBlockRemove,
    userAgent = userAgent,
    extractorOverrides = extractorOverrides.mapValues { (_, v) ->
        io.github.alelk.tgvd.domain.system.YtDlpExtractorOverride(
            legacyServerConnect = v.legacyServerConnect,
            noCheckCertificate = v.noCheckCertificate,
            proxyEnabled = v.proxyEnabled,
        )
    },
)

/**
 * This config with the editable part replaced by [settings]; deployment-only values (binary path,
 * timeouts, retries, update policy) stay as they are.
 */
fun YtDlpConfig.withSettings(settings: YtDlpSettings): YtDlpConfig = copy(
    cookiesFromBrowser = settings.cookiesFromBrowser,
    cookiesContent = settings.cookiesContent,
    cookiesFile = settings.cookiesFile,
    legacyServerConnect = settings.legacyServerConnect,
    noCheckCertificate = settings.noCheckCertificate,
    preferredFormats = settings.preferredFormats,
    formatSort = settings.formatSort,
    checkFormats = settings.checkFormats,
    preferredAudioLanguages = settings.preferredAudioLanguages,
    maxAdditionalAudioTracks = settings.maxAdditionalAudioTracks,
    originalAudioLanguage = settings.originalAudioLanguage,
    rateLimit = settings.rateLimit,
    sleepInterval = settings.sleepInterval,
    maxSleepInterval = settings.maxSleepInterval,
    writeSubs = settings.writeSubs,
    writeAutoSubs = settings.writeAutoSubs,
    preferredSubtitleLanguages = settings.preferredSubtitleLanguages,
    subLangs = settings.subLangs,
    embedSubs = settings.embedSubs,
    sleepSubtitles = settings.sleepSubtitles,
    concurrentFragments = settings.concurrentFragments,
    socketTimeout = settings.socketTimeout,
    youtubePlayerClient = settings.youtubePlayerClient,
    extractorArgs = settings.extractorArgs,
    sponsorBlockRemove = settings.sponsorBlockRemove,
    userAgent = settings.userAgent,
    extractorOverrides = settings.extractorOverrides.mapValues { (_, v) ->
        YtDlpExtractorOverride(v.legacyServerConnect, v.noCheckCertificate, v.proxyEnabled)
    },
)

fun ProxyConfig.toProxySettings(): ProxySettings = ProxySettings(
    enabled = enabled,
    type = when (type) {
        ProxyConfig.ProxyType.HTTP -> ProxyType.HTTP
        ProxyConfig.ProxyType.SOCKS5 -> ProxyType.SOCKS5
    },
    host = host,
    port = port,
    username = username,
    password = password,
)

fun ProxySettings.toProxyConfig(): ProxyConfig = ProxyConfig(
    enabled = enabled,
    type = when (type) {
        ProxyType.HTTP -> ProxyConfig.ProxyType.HTTP
        ProxyType.SOCKS5 -> ProxyConfig.ProxyType.SOCKS5
    },
    host = host,
    port = port,
    username = username,
    password = password,
)
