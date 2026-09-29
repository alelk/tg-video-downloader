package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.features.common.component.SectionCard
import io.github.alelk.tgvd.features.settings.model.CookiesSource
import io.github.alelk.tgvd.features.settings.model.SettingsForm

private val browserOptions = listOf("", "chrome", "firefox", "safari", "brave", "edge", "opera")

private val cookieSourceLabels = listOf(
    CookiesSource.BROWSER to "Browser",
    CookiesSource.TEXT to "Paste Text",
    CookiesSource.FILE to "File Path",
)

@Composable
internal fun CookiesSection(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    // rememberSaveable: Voyager restores it across tab switches; kept here so switching the source keeps it too
    var hintExpanded: Boolean by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "Cookies") {
        Text(
            "Required for age-restricted, private, or members-only content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Cookie source selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            cookieSourceLabels.forEachIndexed { index, (source, label) ->
                SegmentedButton(
                    selected = form.cookiesSource == source,
                    onClick = { onFormChange(form.copy(cookiesSource = source)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = cookieSourceLabels.size),
                ) { Text(label) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (form.cookiesSource) {
            CookiesSource.BROWSER -> BrowserCookies(form, onFormChange)
            CookiesSource.TEXT -> TextCookies(form, hintExpanded, { hintExpanded = !hintExpanded }, onFormChange)
            CookiesSource.FILE -> {
                Text(
                    "Path to a Netscape-format cookies.txt file on the server machine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.cookiesFile,
                    onValueChange = { onFormChange(form.copy(cookiesFile = it)) },
                    label = { Text("Cookies File Path") },
                    placeholder = { Text("/path/to/cookies.txt") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserCookies(form: SettingsForm, onFormChange: (SettingsForm) -> Unit) {
    Text(
        "yt-dlp will read cookies directly from your browser's profile on the server machine.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    var browserExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = browserExpanded, onExpandedChange = { browserExpanded = it }) {
        OutlinedTextField(
            value = form.cookiesFromBrowser.ifBlank { "None" },
            onValueChange = {},
            readOnly = true,
            label = { Text("Browser") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(browserExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = browserExpanded, onDismissRequest = { browserExpanded = false }) {
            browserOptions.forEach { browser ->
                DropdownMenuItem(
                    text = { Text(browser.ifBlank { "None" }) },
                    onClick = {
                        onFormChange(form.copy(cookiesFromBrowser = browser))
                        browserExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TextCookies(
    form: SettingsForm,
    hintExpanded: Boolean,
    onToggleHint: () -> Unit,
    onFormChange: (SettingsForm) -> Unit,
) {
    TextButton(
        onClick = onToggleHint,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            if (hintExpanded) "Hide instructions" else "How to get cookies from browser",
            style = MaterialTheme.typography.labelMedium,
        )
    }

    if (hintExpanded) {
        CookieExportHint()
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = form.cookiesContent,
        onValueChange = { onFormChange(form.copy(cookiesContent = it)) },
        label = { Text("Cookies (Netscape format)") },
        placeholder = { Text("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\t...") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        maxLines = 15,
    )

    if (form.cookiesContent.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { onFormChange(form.copy(cookiesContent = "")) }) {
            Text("Clear cookies", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CookieExportHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "How to export cookies (Netscape format):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "1. Install the \"Get cookies.txt LOCALLY\" extension\n" +
                    "   Chrome: chrome.google.com/webstore → search \"Get cookies.txt\"\n" +
                    "   Firefox: addons.mozilla.org → search \"cookies.txt\"\n\n" +
                    "2. Open the site (e.g. youtube.com) and sign in\n\n" +
                    "3. Click the extension icon → Export → Netscape format\n\n" +
                    "4. Copy all the text and paste it into the field below\n\n" +
                    "Tip: use a private/incognito window for a clean export.\n" +
                    "Docs: github.com/yt-dlp/yt-dlp#cookies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
