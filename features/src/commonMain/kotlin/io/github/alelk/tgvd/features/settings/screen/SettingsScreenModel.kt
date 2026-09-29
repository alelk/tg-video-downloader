package io.github.alelk.tgvd.features.settings.screen

import arrow.core.raise.either
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.features.common.FeatureScreenModel
import io.github.alelk.tgvd.features.settings.model.SettingsForm
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State holder of the settings screen: loads yt-dlp status and settings, updates yt-dlp, saves the form. */
class SettingsScreenModel(private val client: TgVideoDownloaderClient) :
    FeatureScreenModel<SettingsUiState, SettingsEffect>(SettingsUiState()) {

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Opened, SettingsEvent.RetryClicked -> load()
            is SettingsEvent.FormChanged -> mutableState.update { it.copy(form = event.form) }
            SettingsEvent.SubtitlesToggled -> mutableState.update { it.copy(subtitlesExpanded = !it.subtitlesExpanded) }
            SettingsEvent.AdvancedToggled -> mutableState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
            SettingsEvent.UpdateYtDlpClicked -> updateYtDlp()
            SettingsEvent.SaveClicked -> save()
        }
    }

    /**
     * Loads the yt-dlp status, then the settings. The status is shown as soon as it arrives, even if the
     * settings then fail. The loader is shown only while there is no status to show yet.
     */
    private fun load() {
        launchRequest(
            call = {
                either {
                    val status = client.getYtDlpStatus().bind()
                    mutableState.update { it.copy(ytDlp = YtDlpStatus.from(status)) }
                    client.getSettings().bind()
                }
            },
            started = { it.copy(loading = it.ytDlp == null) },
            finished = { it.copy(loading = false) },
            onFailure = { s, error -> s.copy(failure = SettingsFailure(SettingsAction.LOAD, error)) },
            onSuccess = { s, settings ->
                val form = SettingsForm.from(settings)
                // Expand a collapsed section that holds non-default values; never collapse what the person opened.
                s.copy(
                    form = form,
                    subtitlesExpanded = s.subtitlesExpanded || form.hasSubtitleSettings,
                    advancedExpanded = s.advancedExpanded || form.hasAdvancedSettings,
                    failure = null,
                )
            },
        )
    }

    private fun updateYtDlp() {
        if (state.value.ytDlpUpdating) return
        screenModelScope.launch {
            runRequest(
                call = { client.updateYtDlp() },
                started = { it.copy(ytDlpUpdating = true) },
                finished = { it.copy(ytDlpUpdating = false) },
                onFailure = { s, error -> s.copy(failure = SettingsFailure(SettingsAction.UPDATE_YT_DLP, error)) },
                onSuccess = { s, _ -> s },
            ).onRight { load() }
        }
    }

    private fun save() {
        val current = state.value
        if (current.busy) return
        val request = current.form.toRequest()
        screenModelScope.launch {
            runRequest(
                call = { client.updateSettings(request) },
                started = { it.copy(busy = true, failure = null) },
                finished = { it.copy(busy = false) },
                onFailure = { s, error -> s.copy(failure = SettingsFailure(SettingsAction.SAVE, error)) },
                onSuccess = { s, _ -> s },
            ).onRight { sendEffect(SettingsEffect.Saved) }
        }
    }
}
