package io.github.alelk.tgvd.features.settings.screen

import io.github.alelk.tgvd.api.client.ApiError
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.features.common.Async
import io.github.alelk.tgvd.features.settings.model.SettingsForm

/**
 * Everything the settings screen draws.
 *
 * [loading] — the yt-dlp status is being loaded for the first time (the spinner in the yt-dlp card);
 * [busy] — the settings are being saved; [ytDlpUpdating] — the yt-dlp update is running.
 * [subtitlesExpanded]/[advancedExpanded] survive tab switches together with the screen model.
 */
data class SettingsUiState(
    val form: SettingsForm = SettingsForm(),
    val ytDlp: YtDlpStatus? = null,
    val subtitlesExpanded: Boolean = false,
    val advancedExpanded: Boolean = false,
    val ytDlpUpdating: Boolean = false,
    override val loading: Boolean = true,
    override val busy: Boolean = false,
    val failure: SettingsFailure? = null,
) : Async {
    override val error: ApiError? get() = failure?.error
}

data class YtDlpStatus(
    val currentVersion: String,
    val latestVersion: String?,
    val lastCheckedAt: String?,
    val isUpdateAvailable: Boolean,
) {
    companion object {
        fun from(dto: YtDlpStatusDto): YtDlpStatus = YtDlpStatus(
            currentVersion = dto.currentVersion,
            latestVersion = dto.latestVersion,
            lastCheckedAt = dto.lastCheckedAt,
            isUpdateAvailable = dto.isUpdateAvailable,
        )
    }
}

/** A failed request and which action it belonged to (the fallback text depends on the action). */
data class SettingsFailure(val action: SettingsAction, val error: ApiError)

enum class SettingsAction { LOAD, UPDATE_YT_DLP, SAVE }

/** The selected workspace, as the settings screen shows it. */
data class WorkspaceSummary(val name: String, val slug: String, val role: String)
