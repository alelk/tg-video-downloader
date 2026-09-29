package io.github.alelk.tgvd.features.download.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.generated.resources.Res
import io.github.alelk.tgvd.features.generated.resources.preview_audio_tracks
import io.github.alelk.tgvd.features.generated.resources.preview_auto_subtitles
import io.github.alelk.tgvd.features.generated.resources.preview_media_tracks
import io.github.alelk.tgvd.features.generated.resources.preview_select_audio
import io.github.alelk.tgvd.features.generated.resources.preview_subtitle_tracks
import org.jetbrains.compose.resources.stringResource

/** Audio tracks (at least one must stay selected) and subtitle languages (collapsed list). */
@Composable
internal fun MediaTracksSection(state: PreviewUiState, onEvent: (PreviewEvent) -> Unit) {
    SectionCard(title = stringResource(Res.string.preview_media_tracks)) {
        if (state.audioOptions.isNotEmpty()) {
            Text(stringResource(Res.string.preview_audio_tracks), style = MaterialTheme.typography.titleSmall)
            state.audioOptions.forEach { track ->
                val selected = track.formatId in state.selectedAudioIds
                CheckableRow(
                    checked = selected,
                    onCheckedChange = { onEvent(PreviewEvent.AudioTrackChecked(track.formatId, it)) },
                    label = track.label,
                )
            }
            if (state.selectedAudioIds.isEmpty()) {
                Text(stringResource(Res.string.preview_select_audio), color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.subtitleOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SubtitleTracks(
                options = state.subtitleOptions,
                selectedLanguages = state.selectedSubtitleLanguages,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun SubtitleTracks(
    options: List<SubtitleOption>,
    selectedLanguages: List<String>,
    onEvent: (PreviewEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.preview_subtitle_tracks), style = MaterialTheme.typography.titleSmall)
            Text(
                selectedLanguages.joinToString(", ").ifEmpty { "None selected" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Collapse" else "Edit")
        }
    }
    if (expanded) {
        val autoSuffix = " (${stringResource(Res.string.preview_auto_subtitles)})"
        options.forEach { option ->
            CheckableRow(
                checked = option.language in selectedLanguages,
                onCheckedChange = { onEvent(PreviewEvent.SubtitleLanguageChecked(option.language, it)) },
                label = option.language + if (option.automaticOnly) autoSuffix else "",
            )
        }
    }
}

/** A row that toggles on click anywhere, with the checkbox at the start. */
@Composable
private fun CheckableRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
