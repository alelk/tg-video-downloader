package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.BuildConfig
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.InfoRow
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.theme.StatusCompleted
import io.github.alelk.tgvd.features.settings.model.SettingsForm

/** The settings screen, drawn from [state] alone; every action goes out through [onEvent]. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    workspace: WorkspaceSummary?,
    savedNoticeVisible: Boolean,
    onEvent: (SettingsEvent) -> Unit,
) {
    val form = state.form
    val onFormChange: (SettingsForm) -> Unit = { onEvent(SettingsEvent.FormChanged(it)) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        state.failure?.let { failure ->
            ErrorCard(message = failure.text(), onRetry = { onEvent(SettingsEvent.RetryClicked) })
        }

        if (savedNoticeVisible) SavedNotice()

        WorkspaceSection(workspace)
        YtDlpSection(state = state, onEvent = onEvent)
        CookiesSection(form = form, onFormChange = onFormChange)
        SslSection(form = form, onFormChange = onFormChange)
        FormatsSection(form = form, onFormChange = onFormChange)
        SubtitlesSection(
            form = form,
            expanded = state.subtitlesExpanded,
            onToggle = { onEvent(SettingsEvent.SubtitlesToggled) },
            onFormChange = onFormChange,
        )
        ProxySection(form = form, onFormChange = onFormChange)
        AdvancedSection(
            form = form,
            expanded = state.advancedExpanded,
            onToggle = { onEvent(SettingsEvent.AdvancedToggled) },
            onFormChange = onFormChange,
        )

        Button(
            onClick = { onEvent(SettingsEvent.SaveClicked) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (state.busy) "Saving..." else "Save Settings")
        }

        SectionCard(title = "About") {
            InfoRow("App", "TG Video Downloader")
            InfoRow("Version", BuildConfig.APP_VERSION)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SavedNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            "Settings saved",
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun WorkspaceSection(workspace: WorkspaceSummary?) {
    SectionCard(title = "Workspace") {
        if (workspace != null) {
            InfoRow("Name", workspace.name)
            InfoRow("Slug", workspace.slug)
            InfoRow("Role", workspace.role)
        } else {
            Text("No workspace selected", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun YtDlpSection(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    SectionCard(title = "yt-dlp") {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            return@SectionCard
        }
        val status = state.ytDlp ?: return@SectionCard
        InfoRow("Version", status.currentVersion)
        status.latestVersion?.let { InfoRow("Latest", it) }
        status.lastCheckedAt?.let { InfoRow("Checked", it) }
        Spacer(modifier = Modifier.height(8.dp))
        if (status.isUpdateAvailable) {
            Button(
                onClick = { onEvent(SettingsEvent.UpdateYtDlpClicked) },
                enabled = !state.ytDlpUpdating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.ytDlpUpdating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.ytDlpUpdating) "Updating..." else "Update yt-dlp")
            }
        } else {
            Text("Up to date", style = MaterialTheme.typography.bodyMedium, color = StatusCompleted)
        }
    }
}

/** A setting with a title, a one-line explanation under it and a switch at the end. */
@Composable
internal fun DescribedSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = descriptionColor)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The texts shown before stage 01.12: the server's message, or a per-action fallback. */
private fun SettingsFailure.text(): String = error.message ?: when (action) {
    SettingsAction.LOAD -> "Failed to load settings"
    SettingsAction.UPDATE_YT_DLP -> "Update failed"
    SettingsAction.SAVE -> "Failed to save settings"
}
