package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import org.koin.compose.koinInject

/**
 * Entry of the settings screen: acquires [SettingsScreenModel], (re)loads the settings every time the screen
 * is shown, turns the "saved" effect into the confirmation card and hands the rest to [SettingsContent].
 */
@Composable
internal fun Screen.SettingsEntry() {
    val screenModel = koinScreenModel<SettingsScreenModel>()
    val workspaceState = koinInject<WorkspaceState>()
    val state by screenModel.state.collectAsStateWithLifecycle()
    // Shown from a successful save until the next save starts or the screen is left.
    var savedNoticeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(screenModel) {
        screenModel.onEvent(SettingsEvent.Opened)
        screenModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.Saved -> savedNoticeVisible = true
            }
        }
    }

    SettingsContent(
        state = state,
        workspace = workspaceState.selectedWorkspace?.let {
            WorkspaceSummary(name = it.name, slug = it.slug, role = it.role)
        },
        savedNoticeVisible = savedNoticeVisible,
        onEvent = { event ->
            if (event == SettingsEvent.SaveClicked) savedNoticeVisible = false
            screenModel.onEvent(event)
        },
    )
}
