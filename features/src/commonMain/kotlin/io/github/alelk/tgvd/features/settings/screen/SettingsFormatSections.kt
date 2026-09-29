package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.settings.model.SettingsForm

@Composable
internal fun SslSection(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    SectionCard(title = "SSL / TLS") {
        DescribedSwitchRow(
            title = "Legacy server connect",
            description = "Fix SSL errors on some sites (e.g. RuTube)",
            checked = form.legacyServerConnect,
            onCheckedChange = { onFormChange(form.copy(legacyServerConnect = it)) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        DescribedSwitchRow(
            title = "No check certificate",
            description = "Disable TLS validation — use with caution!",
            checked = form.noCheckCertificate,
            onCheckedChange = { onFormChange(form.copy(noCheckCertificate = it)) },
            descriptionColor = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun FormatsSection(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    SectionCard(title = "Format & Quality") {
        Text(
            "Override automatic format selection. Leave empty to use the quality selected per job.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        AudioTrackSettings(form, onFormChange)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.preferredFormats,
            onValueChange = { onFormChange(form.copy(preferredFormats = it)) },
            label = { Text("Format selector (-f)") },
            placeholder = { Text("bestvideo[height<=1080]+bestaudio/best") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("yt-dlp format string. Overrides per-job quality if set.") },
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.formatSort,
            onValueChange = { onFormChange(form.copy(formatSort = it)) },
            label = { Text("Format sort (-S)") },
            placeholder = { Text("res,tbr,fps") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Used when Format selector is empty. Example: res:1080,tbr,fps") },
        )

        Spacer(modifier = Modifier.height(8.dp))

        DescribedSwitchRow(
            title = "Check formats",
            description = "Verify format availability before download (may fail on some sites)",
            checked = form.checkFormats,
            onCheckedChange = { onFormChange(form.copy(checkFormats = it)) },
        )
    }
}

@Composable
private fun AudioTrackSettings(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    Text("Audio tracks", style = MaterialTheme.typography.titleMedium)
    Text(
        "The source-original audio is always included and set as default. " +
            "Optional languages are added when available; MKV is recommended for multiple tracks.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.originalAudioLanguage,
        onValueChange = { onFormChange(form.copy(originalAudioLanguage = it)) },
        label = { Text("Original audio language (override)") },
        placeholder = { Text("ru") },
        supportingText = {
            Text(
                "Guarantees this language is used as the original/default track, " +
                    "even if YouTube reports a dub as default. Leave empty to trust auto-detection.",
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.preferredAudioLanguages,
        onValueChange = { onFormChange(form.copy(preferredAudioLanguages = it)) },
        label = { Text("Additional audio languages") },
        placeholder = { Text("ru, en") },
        supportingText = {
            Text(
                "Comma-separated BCP 47 language codes, in priority order. Empty = original track only. " +
                    "Rules and channels can override this.",
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    MaxAdditionalTracksField(form, onFormChange)
}

@Composable
private fun MaxAdditionalTracksField(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    OutlinedTextField(
        value = form.maxAdditionalAudioTracks,
        onValueChange = { onFormChange(form.copy(maxAdditionalAudioTracks = it.filter(Char::isDigit))) },
        label = { Text("Maximum additional tracks") },
        supportingText = {
            Text(
                "0–8; the original track is not counted. Set to 0 to download only the original audio, " +
                    "with no translations.",
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    if (form.preferredFormats.isNotBlank()) {
        Text(
            "The custom format selector overrides automatic audio-language selection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
