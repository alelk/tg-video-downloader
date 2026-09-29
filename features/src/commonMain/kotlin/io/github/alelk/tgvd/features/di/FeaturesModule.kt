package io.github.alelk.tgvd.features.di

import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage
import io.github.alelk.tgvd.features.common.state.LocaleState
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import io.github.alelk.tgvd.features.download.screen.PreviewScreenModel
import io.github.alelk.tgvd.features.settings.screen.SettingsScreenModel
import org.koin.dsl.module

/**
 * Koin module for features.
 * Uses optional [PreferencesStorage] for persisting user preferences (e.g. selected workspace, locale).
 * Screen models are factories: Voyager keeps one per screen (`koinScreenModel`) and disposes it with the screen.
 */
val featuresModule = module {
    single { WorkspaceState(preferences = getOrNull<PreferencesStorage>()) }
    single { LocaleState(preferences = getOrNull<PreferencesStorage>()) }

    factory { SettingsScreenModel(client = get()) }
    factory { params -> PreviewScreenModel(client = get(), initialPreview = params.get()) }
}
