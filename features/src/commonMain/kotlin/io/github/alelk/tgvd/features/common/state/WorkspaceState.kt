package io.github.alelk.tgvd.features.common.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto

/**
 * Holds workspace state across the app.
 * Injected via Koin and shared between all screens.
 */
class WorkspaceState {
    var workspaces by mutableStateOf<List<WorkspaceDto>>(emptyList())
    var selectedWorkspace by mutableStateOf<WorkspaceDto?>(null)

    val selectedWorkspaceId: String?
        get() = selectedWorkspace?.id
}

