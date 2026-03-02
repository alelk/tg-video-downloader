package io.github.alelk.tgvd.features.di

import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import org.koin.dsl.module

/**
 * Koin module for features.
 * Uses optional [PreferencesStorage] for persisting user preferences (e.g. selected workspace).
 */
val featuresModule = module {
    single { WorkspaceState(preferences = getOrNull<PreferencesStorage>()) }
}
