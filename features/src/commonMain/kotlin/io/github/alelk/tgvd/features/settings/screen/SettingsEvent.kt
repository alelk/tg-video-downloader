package io.github.alelk.tgvd.features.settings.screen

import io.github.alelk.tgvd.features.settings.model.SettingsForm

/** What the person did on the settings screen (plus [Opened]: the screen became visible). */
sealed interface SettingsEvent {
    /** The screen entered composition (first time, or back from another tab): the settings are (re)loaded. */
    data object Opened : SettingsEvent

    data object RetryClicked : SettingsEvent

    /** Any edit of the form; carries the whole edited form. */
    data class FormChanged(val form: SettingsForm) : SettingsEvent

    data object SubtitlesToggled : SettingsEvent

    data object AdvancedToggled : SettingsEvent

    data object UpdateYtDlpClicked : SettingsEvent

    data object SaveClicked : SettingsEvent
}

/** One-shot outcomes for the Entry. */
sealed interface SettingsEffect {
    /** The settings were saved: show the confirmation. */
    data object Saved : SettingsEffect
}
