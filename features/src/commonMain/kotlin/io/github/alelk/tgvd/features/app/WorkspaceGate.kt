package io.github.alelk.tgvd.features.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import arrow.core.raise.either
import io.github.alelk.tgvd.api.client.ApiError
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClientImpl
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import org.koin.compose.koinInject

/**
 * Initializes the workspace before rendering the main UI.
 * Fetches the user's workspaces and creates one if none exist; restores the previously selected
 * workspace from preferences and scopes the client to it, so all later API calls hit that workspace.
 */
@Composable
fun WorkspaceGate(content: @Composable () -> Unit) {
    val client = koinInject<TgVideoDownloaderClient>()
    val workspaceState = koinInject<WorkspaceState>()
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryTrigger) {
        error = null
        ready = false
        either {
            val existing = client.getWorkspaces().bind().items
            if (existing.isNotEmpty()) {
                existing
            } else {
                // POST /workspaces is get-or-create on the server: an existing "default" slug returns 200.
                listOf(client.createWorkspace(CreateWorkspaceRequestDto(slug = "default", name = "Default")).bind())
            }
        }.fold(
            ifLeft = { error = it.initializationMessage() },
            ifRight = { workspaces ->
                workspaceState.workspaces = workspaces
                // Restore the previously selected workspace or use the first one
                val selected = workspaceState.savedSlug?.let { slug -> workspaces.find { it.slug == slug } }
                    ?: workspaces.first()
                workspaceState.selectWorkspace(selected)
                (client as TgVideoDownloaderClientImpl).workspaceSlug = selected.slug
                ready = true
            },
        )
    }

    // Sync the client's workspaceSlug when the selection changes
    LaunchedEffect(workspaceState.selectedWorkspace) {
        workspaceState.selectedWorkspace?.let { ws ->
            (client as TgVideoDownloaderClientImpl).workspaceSlug = ws.slug
        }
    }

    when {
        error != null -> InitializationError(message = error.orEmpty(), onRetry = { retryTrigger++ })
        ready -> content()
        else ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
    }
}

private fun ApiError.initializationMessage(): String = when (this) {
    is ApiError.Http -> "$code: $message"
    is ApiError.Network, is ApiError.Decoding -> message ?: "Failed to initialize workspace"
}

@Composable
private fun InitializationError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "⚠️ Initialization Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
