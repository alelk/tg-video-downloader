package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.settings.model.SettingsForm

/** Subtitles (collapsible). */
@Composable
internal fun SubtitlesSection(
    form: SettingsForm,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFormChange: (SettingsForm) -> Unit,
) {
    SectionCard(title = "Subtitles") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Subtitles settings", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Collapse" else "Expand")
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            SwitchRow("Download subtitles", form.writeSubs) { onFormChange(form.copy(writeSubs = it)) }
            SwitchRow("Auto-generated subtitles", form.writeAutoSubs) { onFormChange(form.copy(writeAutoSubs = it)) }
            if (form.writeSubs || form.writeAutoSubs) {
                SubtitleDetails(form, onFormChange)
            }
        }
    }
}

@Composable
private fun SubtitleDetails(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.subLangs,
        onValueChange = { onFormChange(form.copy(subLangs = it)) },
        label = { Text("Subtitle languages") },
        placeholder = { Text("ru,en") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Comma-separated language codes") },
    )
    Spacer(modifier = Modifier.height(8.dp))
    DescribedSwitchRow(
        title = "Embed subtitles",
        description = "Requires ffmpeg",
        checked = form.embedSubs,
        onCheckedChange = { onFormChange(form.copy(embedSubs = it)) },
    )
    if (form.writeSubs && form.writeAutoSubs) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = form.sleepSubtitles,
            onValueChange = { onFormChange(form.copy(sleepSubtitles = it)) },
            label = { Text("Sleep before subtitles (s)") },
            placeholder = { Text("3") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text(
                    "Pause before each subtitle request — avoids YouTube 429 " +
                        "when fetching both regular and auto captions",
                )
            },
        )
    }
}

/** A title and a switch, spread across the row. */
@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
