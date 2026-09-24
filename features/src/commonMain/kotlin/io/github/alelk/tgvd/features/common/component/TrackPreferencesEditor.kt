package io.github.alelk.tgvd.features.common.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.contract.storage.TrackPreferencesDto

/**
 * Form state for optional audio/subtitle track overrides (rule or channel level).
 * Unset values inherit from the lower-priority level: global settings → rule → channel.
 */
data class TrackPreferencesForm(
    /** false = inherit audio languages; true = use [audioLanguages] (empty = original track only). */
    val overrideAudio: Boolean = false,
    val audioLanguages: String = "",
    /** null = inherit; true/false = force subtitles on/off. */
    val downloadSubtitles: Boolean? = null,
    /** Empty = inherit subtitle languages. */
    val subtitleLanguages: String = "",
) {
    fun toDto(): TrackPreferencesDto = TrackPreferencesDto(
        audioLanguages = if (overrideAudio) audioLanguages.parseLanguages() else null,
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages.parseLanguages().takeIf { it.isNotEmpty() },
    )

    companion object {
        fun from(dto: TrackPreferencesDto?): TrackPreferencesForm = TrackPreferencesForm(
            overrideAudio = dto?.audioLanguages != null,
            audioLanguages = dto?.audioLanguages.orEmpty().joinToString(", "),
            downloadSubtitles = dto?.downloadSubtitles,
            subtitleLanguages = dto?.subtitleLanguages.orEmpty().joinToString(", "),
        )
    }
}

private fun String.parseLanguages(): List<String> = split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()

/**
 * Editor for [TrackPreferencesForm].
 * @param inheritFrom human-readable name of the level unset values fall back to (e.g. "global settings").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackPreferencesEditor(
    form: TrackPreferencesForm,
    onChange: (TrackPreferencesForm) -> Unit,
    inheritFrom: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        Text("Audio tracks", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = !form.overrideAudio, onClick = { onChange(form.copy(overrideAudio = false)) },
                label = { Text("Inherit", style = MaterialTheme.typography.bodySmall) })
            FilterChip(selected = form.overrideAudio, onClick = { onChange(form.copy(overrideAudio = true)) },
                label = { Text("Override", style = MaterialTheme.typography.bodySmall) })
        }
        if (form.overrideAudio) {
            OutlinedTextField(form.audioLanguages, { onChange(form.copy(audioLanguages = it)) },
                label = { Text("Additional audio languages") }, placeholder = { Text("ru, en") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Hint(
            if (form.overrideAudio) "The original track is always downloaded. Comma-separated languages (ru, en) " +
                "add dubbed/translated tracks when the video has them. Empty = original track only."
            else "Uses the audio languages from $inheritFrom.",
        )

        Text("Subtitles", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = form.downloadSubtitles == null, onClick = { onChange(form.copy(downloadSubtitles = null)) },
                label = { Text("Inherit", style = MaterialTheme.typography.bodySmall) })
            FilterChip(selected = form.downloadSubtitles == true, onClick = { onChange(form.copy(downloadSubtitles = true)) },
                label = { Text("Always", style = MaterialTheme.typography.bodySmall) })
            FilterChip(selected = form.downloadSubtitles == false, onClick = { onChange(form.copy(downloadSubtitles = false)) },
                label = { Text("Never", style = MaterialTheme.typography.bodySmall) })
        }
        Hint("Inherit follows $inheritFrom. Always/Never force subtitles on or off.")
        if (form.downloadSubtitles != false) {
            OutlinedTextField(form.subtitleLanguages, { onChange(form.copy(subtitleLanguages = it)) },
                label = { Text("Subtitle languages") }, placeholder = { Text("ru, en") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Hint("Comma-separated (en, ru, de). Empty = languages from $inheritFrom. Unavailable languages are skipped.")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
