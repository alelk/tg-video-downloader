package io.github.alelk.tgvd.features.di

import io.github.alelk.tgvd.features.common.state.WorkspaceState
import org.koin.dsl.module

/**
 * Koin module for features. Currently empty — ViewModels are injected
 * via koinInject() composables. Add ViewModel factories here if needed.
 */
val featuresModule = module {
    single { WorkspaceState() }
}
