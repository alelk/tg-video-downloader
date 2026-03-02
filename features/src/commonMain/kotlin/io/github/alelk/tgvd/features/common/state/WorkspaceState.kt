package io.github.alelk.tgvd.features.common.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage

/**
 * Holds workspace state across the app.
 * Injected via Koin and shared between all screens.
 *
 * Optionally persists the selected workspace slug in [PreferencesStorage]
 * so the choice survives app restarts.
 */
class WorkspaceState(
    private val preferences: PreferencesStorage? = null,
) {
    var workspaces by mutableStateOf<List<WorkspaceDto>>(emptyList())
    var selectedWorkspace by mutableStateOf<WorkspaceDto?>(null)

    val selectedWorkspaceSlug: String?
        get() = selectedWorkspace?.slug

    /** Slug saved from the previous session (read from persistent storage). */
    val savedSlug: String?
        get() = preferences?.get(PREF_KEY)

    /** Select a workspace and persist the choice. */
    fun selectWorkspace(workspace: WorkspaceDto) {
        selectedWorkspace = workspace
        preferences?.set(PREF_KEY, workspace.slug)
    }

    companion object {
        private const val PREF_KEY = "selected_workspace_slug"
    }
}
