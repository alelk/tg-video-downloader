package io.github.alelk.tgvd.api.mapping.system

import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpExtractorOverrideDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.github.alelk.tgvd.domain.system.ProxySettingsRequest
import io.github.alelk.tgvd.domain.system.ProxyType
import io.github.alelk.tgvd.domain.system.SystemSettings
import io.github.alelk.tgvd.domain.system.UpdateSystemSettingsRequest
import io.github.alelk.tgvd.domain.system.YtDlpExtractorOverride
import io.github.alelk.tgvd.domain.system.YtDlpSettings

/** `GET /system/settings`: the secrets ([YtDlpSettings.cookiesContent], the proxy password) are never sent. */
fun SystemSettings.toDto(): SystemSettingsDto = SystemSettingsDto(
    ytDlp = ytDlp.toDto(),
    proxy =
    ProxySettingsDto(
        enabled = proxy.enabled,
        type = proxy.type.name,
        host = proxy.host,
        port = proxy.port,
        username = proxy.username,
        password = null,
    ),
)

/**
 * `PUT /system/settings` body → [UpdateSystemSettingsRequest]. Never fails: an unknown proxy `type`
 * (matched ignoring case) becomes `null` — "keep the stored type".
 */
fun SystemSettingsDto.toUpdateRequest(): UpdateSystemSettingsRequest = UpdateSystemSettingsRequest(
    ytDlp = ytDlp.toDomain(),
    proxy =
    ProxySettingsRequest(
        enabled = proxy.enabled,
        type = ProxyType.entries.find { it.name.equals(proxy.type, ignoreCase = true) },
        host = proxy.host,
        port = proxy.port,
        username = proxy.username,
        password = proxy.password,
    ),
)

/**
 * The response of `PUT /system/settings`: the request as received (not normalised) with the proxy
 * password removed. Kept exactly like this for wire compatibility (G1).
 */
fun SystemSettingsDto.toUpdateResponse(): SystemSettingsDto = copy(proxy = proxy.copy(password = null))

private fun YtDlpSettings.toDto(): YtDlpSettingsDto = YtDlpSettingsDto(
    cookiesFromBrowser = cookiesFromBrowser,
    cookiesContent = null,
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
    extractorOverrides = extractorOverrides.mapValues { (_, v) -> v.toDto() },
)

private fun YtDlpSettingsDto.toDomain(): YtDlpSettings = YtDlpSettings(
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
    extractorOverrides = extractorOverrides.mapValues { (_, v) -> v.toDomain() },
)

private fun YtDlpExtractorOverride.toDto() = YtDlpExtractorOverrideDto(
    legacyServerConnect = legacyServerConnect,
    noCheckCertificate = noCheckCertificate,
    proxyEnabled = proxyEnabled,
)

private fun YtDlpExtractorOverrideDto.toDomain() = YtDlpExtractorOverride(
    legacyServerConnect = legacyServerConnect,
    noCheckCertificate = noCheckCertificate,
    proxyEnabled = proxyEnabled,
)
