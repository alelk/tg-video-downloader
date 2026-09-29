package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.alelk.tgvd.features.settings.model.SettingsForm

/**
 * YouTube player clients. "ios" and "android" work without a JS runtime (deno/node).
 * "web" requires deno but gives the most formats.
 */
private val youtubePlayerClients = listOf("ios", "android", "web", "mweb", "tv_embedded", "")

/** Advanced yt-dlp settings (collapsible): rate limiting, performance, site-specific. */
@Composable
internal fun AdvancedSection(
    form: SettingsForm,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFormChange: (SettingsForm) -> Unit,
) {
    SectionCard(title = "Advanced yt-dlp Settings") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Rate limiting, performance, site-specific",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Collapse" else "Expand")
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            RateLimitSettings(form, onFormChange)
            Spacer(modifier = Modifier.height(12.dp))
            PerformanceSettings(form, onFormChange)
            Spacer(modifier = Modifier.height(12.dp))
            SiteSpecificSettings(form, onFormChange)
        }
    }
}

@Composable
private fun RateLimitSettings(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    Text("Rate Limiting", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
        value = form.rateLimit,
        onValueChange = { onFormChange(form.copy(rateLimit = it)) },
        label = { Text("Rate limit") },
        placeholder = { Text("5M") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Max download speed, e.g. 5M, 500K. Empty = unlimited.") },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.sleepInterval,
            onValueChange = { onFormChange(form.copy(sleepInterval = it)) },
            label = { Text("Sleep interval (s)") },
            placeholder = { Text("2") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            supportingText = { Text("Pause between requests") },
        )
        OutlinedTextField(
            value = form.maxSleepInterval,
            onValueChange = { onFormChange(form.copy(maxSleepInterval = it)) },
            label = { Text("Max sleep (s)") },
            placeholder = { Text("5") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PerformanceSettings(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    Text("Performance", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.concurrentFragments,
            onValueChange = { onFormChange(form.copy(concurrentFragments = it)) },
            label = { Text("Concurrent fragments") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            supportingText = { Text("Default: 5") },
        )
        OutlinedTextField(
            value = form.socketTimeout,
            onValueChange = { onFormChange(form.copy(socketTimeout = it)) },
            label = { Text("Socket timeout (s)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            supportingText = { Text("Default: 30") },
        )
    }
}

@Composable
private fun SiteSpecificSettings(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    Text("Site-specific", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    PlayerClientDropdown(form, onFormChange)
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.extractorArgs,
        onValueChange = { onFormChange(form.copy(extractorArgs = it)) },
        label = { Text("Extractor args (advanced)") },
        placeholder = { Text("vk:nocheckcertificate=1") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = {
            Text(
                "--extractor-args for non-YouTube extractors. " +
                    "For YouTube player client use the dropdown above. " +
                    "If this field contains 'player_client', it takes full priority.",
            )
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.sponsorBlockRemove,
        onValueChange = { onFormChange(form.copy(sponsorBlockRemove = it)) },
        label = { Text("SponsorBlock remove") },
        placeholder = { Text("sponsor,selfpromo") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Comma-separated categories to cut from video") },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.userAgent,
        onValueChange = { onFormChange(form.copy(userAgent = it)) },
        label = { Text("User-Agent") },
        placeholder = { Text("Mozilla/5.0 ...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerClientDropdown(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = form.youtubePlayerClient.ifBlank { "yt-dlp default (web)" },
            onValueChange = {},
            readOnly = true,
            label = { Text("YouTube Player Client") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            supportingText = { Text(playerClientHint(form.youtubePlayerClient)) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            youtubePlayerClients.forEach { client ->
                DropdownMenuItem(
                    text = { Text(playerClientOption(client)) },
                    onClick = {
                        onFormChange(form.copy(youtubePlayerClient = client))
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun playerClientHint(client: String): String = when (client) {
    "ios" -> "No JS runtime (deno) needed"
    "android" -> "No JS runtime (deno) needed"
    "web" -> "Requires deno installed on server"
    "mweb" -> "No JS runtime needed, lower quality"
    "tv_embedded" -> "No JS runtime needed"
    "" -> "yt-dlp default (web) — requires deno"
    else -> ""
}

private fun playerClientOption(client: String): String = when (client) {
    "ios" -> "ios  (recommended, no deno needed)"
    "android" -> "android  (no deno needed)"
    "web" -> "web  (most formats, requires deno)"
    "mweb" -> "mweb  (mobile, no deno)"
    "tv_embedded" -> "tv_embedded  (no deno)"
    "" -> "yt-dlp default (web, requires deno)"
    else -> client
}
