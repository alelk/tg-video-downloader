package io.github.alelk.tgvd.domain.system

import io.github.alelk.tgvd.domain.tx.TransactionRunner

/**
 * New settings as requested by the administrator.
 *
 * - `null` [YtDlpSettings.cookiesContent] and `null` [ProxySettingsRequest.password] keep the stored
 *   secret: the settings screen never receives the secrets, so it cannot send them back.
 * - [ProxySettingsRequest.type] `null` (a type the server does not know) keeps the stored type.
 */
data class UpdateSystemSettingsRequest(val ytDlp: YtDlpSettings, val proxy: ProxySettingsRequest)

/** The proxy part of [UpdateSystemSettingsRequest]. */
data class ProxySettingsRequest(
    val enabled: Boolean,
    val type: ProxyType?,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
)

/**
 * Replaces the system settings with [UpdateSystemSettingsRequest], normalised:
 * - language codes are trimmed, lowercased, `_` → `-`, blanks and duplicates dropped;
 * - the legacy [YtDlpSettings.subLangs], when given, becomes [YtDlpSettings.preferredSubtitleLanguages]
 *   and is not stored;
 * - [YtDlpSettings.maxAdditionalAudioTracks] is clamped to `0..`[MAX_ADDITIONAL_AUDIO_TRACKS];
 * - missing secrets and an unknown proxy type keep the stored values.
 *
 * @return the settings now in effect.
 */
class UpdateSystemSettingsUseCase(
    private val settingsStore: SystemSettingsStore,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(request: UpdateSystemSettingsRequest): SystemSettings = txRunner.inRwTransaction {
        val current = settingsStore.current()
        val updated =
            SystemSettings(
                ytDlp = request.ytDlp.normalized(keepCookiesContent = current.ytDlp.cookiesContent),
                proxy =
                ProxySettings(
                    enabled = request.proxy.enabled,
                    type = request.proxy.type ?: current.proxy.type,
                    host = request.proxy.host,
                    port = request.proxy.port,
                    username = request.proxy.username,
                    password = request.proxy.password ?: current.proxy.password,
                ),
            )
        settingsStore.save(updated)
        updated
    }

    private fun YtDlpSettings.normalized(keepCookiesContent: String?): YtDlpSettings = copy(
        cookiesContent = cookiesContent ?: keepCookiesContent,
        preferredAudioLanguages = preferredAudioLanguages.normalizedLanguages(),
        maxAdditionalAudioTracks = maxAdditionalAudioTracks.coerceIn(0, MAX_ADDITIONAL_AUDIO_TRACKS),
        originalAudioLanguage = originalAudioLanguage?.normalizedLanguage()?.takeIf { it.isNotBlank() },
        preferredSubtitleLanguages = (subLangs?.split(',') ?: preferredSubtitleLanguages).normalizedLanguages(),
        subLangs = null,
    )

    private fun List<String>.normalizedLanguages(): List<String> =
        map { it.normalizedLanguage() }.filter { it.isNotBlank() }.distinct()

    private fun String.normalizedLanguage(): String = trim().replace('_', '-').lowercase()

    companion object {
        /** The largest number of non-original audio tracks a download may carry. */
        const val MAX_ADDITIONAL_AUDIO_TRACKS = 8
    }
}
