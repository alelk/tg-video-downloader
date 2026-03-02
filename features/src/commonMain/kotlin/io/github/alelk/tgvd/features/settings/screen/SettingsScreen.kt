package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.features.common.component.*
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import io.github.alelk.tgvd.features.common.theme.StatusCompleted
import io.github.alelk.tgvd.features.common.theme.StatusPending
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsScreen() {
    val client = koinInject<TgVideoDownloaderClient>()
    val workspaceState = koinInject<WorkspaceState>()

    var ytDlpStatus by remember { mutableStateOf<YtDlpStatusDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUpdating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadStatus() {
        scope.launch {
            try {
                isLoading = ytDlpStatus == null
                ytDlpStatus = client.getYtDlpStatus()
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load status"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadStatus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        errorMessage?.let {
            ErrorCard(message = it, onRetry = { loadStatus() })
        }

        // Workspace selector
        SectionCard(title = "Workspace") {
            WorkspaceSelector(
                workspaces = workspaceState.workspaces,
                selectedWorkspaceSlug = workspaceState.selectedWorkspaceSlug,
                onWorkspaceSelected = { workspaceState.selectedWorkspace = it },
            )
        }

        // yt-dlp section
        SectionCard(title = "yt-dlp") {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                ytDlpStatus?.let { status ->
                    InfoRow("Version", status.currentVersion)
                    status.latestVersion?.let { InfoRow("Latest", it) }
                    status.lastCheckedAt?.let { InfoRow("Checked", it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (status.isUpdateAvailable) {
                        Button(
                            onClick = {
                                isUpdating = true
                                scope.launch {
                                    try {
                                        client.updateYtDlp()
                                        loadStatus()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Update failed"
                                    } finally {
                                        isUpdating = false
                                    }
                                }
                            },
                            enabled = !isUpdating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isUpdating) "Updating..." else "Update yt-dlp")
                        }
                    } else {
                        Text(
                            "Up to date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusCompleted,
                        )
                    }
                }
            }
        }

        // About section
        SectionCard(title = "About") {
            InfoRow("App", "TG Video Downloader")
            InfoRow("Version", "0.1.0-SNAPSHOT")
        }
    }
}

