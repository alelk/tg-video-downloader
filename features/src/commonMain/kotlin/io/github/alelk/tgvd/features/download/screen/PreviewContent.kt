package io.github.alelk.tgvd.features.download.screen

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.ErrorCard
import io.github.alelk.tgvd.features.common.component.InfoRow
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.common.icon.TgvdIcons
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.action_back
import io.github.alelk.tgvd.features.generated.resources.preview_creating
import io.github.alelk.tgvd.features.generated.resources.preview_download_button
import io.github.alelk.tgvd.features.generated.resources.preview_refetch
import io.github.alelk.tgvd.features.generated.resources.preview_title
import io.github.alelk.tgvd.features.generated.resources.validation_field_has_errors
import org.jetbrains.compose.resources.stringResource

/** The preview screen, drawn from [state] alone; every action goes out through [onEvent] or a navigation lambda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewContent(
    state: PreviewUiState,
    onEvent: (PreviewEvent) -> Unit,
    onBack: () -> Unit,
    onEditChannel: (channelId: String) -> Unit,
    onAddChannel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { PreviewTitle(refreshing = state.loading) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TgvdIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(PreviewEvent.RefetchClicked) }, enabled = state.canRefetch) {
                        Icon(TgvdIcons.Refresh, contentDescription = stringResource(Res.string.preview_refetch))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            VideoInfoSection(state = state, onEditChannel = onEditChannel, onAddChannel = onAddChannel)

            if (state.audioOptions.isNotEmpty() || state.subtitleOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                MediaTracksSection(state = state, onEvent = onEvent)
            }

            // Download History (shown only when there are previous downloads)
            if (state.history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                DownloadHistoryCard(entries = state.history)
            }

            Spacer(modifier = Modifier.height(12.dp))
            MetadataSection(state = state, onEvent = onEvent)

            Spacer(modifier = Modifier.height(12.dp))
            StoragePlanSection(
                editor = state.editor,
                matchedRuleName = state.matchedRule?.name,
                noRule = state.matchedRule == null,
                onEvent = onEvent,
            )

            Spacer(modifier = Modifier.height(12.dp))
            PreviewFooter(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun PreviewTitle(refreshing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.preview_title))
        if (refreshing) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** Matched rule, metadata source, warnings, the error, the validation summary and the download button. */
@Composable
private fun PreviewFooter(state: PreviewUiState, onEvent: (PreviewEvent) -> Unit) {
    // Matched Rule (read-only info)
    state.matchedRule?.let { rule ->
        SectionCard(title = "Matched Rule", icon = TgvdIcons.Rule) {
            InfoRow("Rule", rule.name ?: rule.id)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    SectionCard(title = "Source", icon = TgvdIcons.Label) {
        InfoRow("Metadata source", state.metadataSource)
    }
    Spacer(modifier = Modifier.height(12.dp))

    if (state.warnings.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                state.warnings.forEach { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    state.failure?.let { failure ->
        ErrorCard(message = failure.text())
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (state.fieldErrors.isNotEmpty()) {
        ValidationSummary()
        Spacer(modifier = Modifier.height(8.dp))
    }

    Button(
        onClick = { onEvent(PreviewEvent.DownloadClicked) },
        enabled = state.canDownload,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            if (state.busy) {
                stringResource(Res.string.preview_creating)
            } else {
                stringResource(Res.string.preview_download_button)
            },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ValidationSummary() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                TgvdIcons.ErrorIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(Res.string.validation_field_has_errors),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The texts shown before stage 01.12, per action. */
private fun PreviewFailure.text(): String = when (action) {
    PreviewAction.RE_PREVIEW -> "Re-preview failed: ${error.message}"
    PreviewAction.REFETCH -> "Refetch failed: ${error.message}"
    PreviewAction.CREATE_JOB -> error.message ?: "Failed to create job"
}
