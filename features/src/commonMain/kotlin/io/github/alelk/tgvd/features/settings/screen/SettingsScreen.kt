package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.features.common.BuildConfig
import io.github.alelk.tgvd.features.common.component.*
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import io.github.alelk.tgvd.features.common.theme.StatusCompleted
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val browserOptions = listOf("", "chrome", "firefox", "safari", "brave", "edge", "opera")
private val proxyTypes = listOf("HTTP", "SOCKS5")
private val mergeOutputFormats = listOf("", "mkv", "mp4", "webm", "ogg")

/**
 * YouTube player clients. "ios" and "android" work without a JS runtime (deno/node).
 * "web" requires deno but gives the most formats.
 */
private val youtubePlayerClients = listOf("ios", "android", "web", "mweb", "tv_embedded", "")

/** Which source of cookies is currently active in the UI. */
private enum class CookiesSource { BROWSER, TEXT, FILE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val client = koinInject<TgVideoDownloaderClient>()
    val workspaceState = koinInject<WorkspaceState>()

    var ytDlpStatus by remember { mutableStateOf<YtDlpStatusDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUpdating by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ── Cookies state ─────────────────────────────────────────────────────────
    var cookiesSource by remember { mutableStateOf(CookiesSource.BROWSER) }
    var cookiesFromBrowser by remember { mutableStateOf("") }
    var cookiesContent by remember { mutableStateOf("") }
    var cookiesFile by remember { mutableStateOf("") }

    // ── Proxy state ───────────────────────────────────────────────────────────
    var proxyEnabled by remember { mutableStateOf(false) }
    var proxyType by remember { mutableStateOf("HTTP") }
    var proxyHost by remember { mutableStateOf("127.0.0.1") }
    var proxyPort by remember { mutableStateOf("8080") }
    var proxyUsername by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }

    // ── SSL state ─────────────────────────────────────────────────────────────
    var legacyServerConnect by remember { mutableStateOf(false) }
    var noCheckCertificate by remember { mutableStateOf(false) }

    // ── Format state ──────────────────────────────────────────────────────────
    var preferredFormats by remember { mutableStateOf("") }
    var formatSort by remember { mutableStateOf("") }
    var checkFormats by remember { mutableStateOf(true) }
    var mergeOutputFormat by remember { mutableStateOf("") }

    // ── Rate limiting state ───────────────────────────────────────────────────
    var rateLimit by remember { mutableStateOf("") }
    var sleepInterval by remember { mutableStateOf("") }
    var maxSleepInterval by remember { mutableStateOf("") }

    // ── Subtitles state ───────────────────────────────────────────────────────
    var writeSubs by remember { mutableStateOf(false) }
    var writeAutoSubs by remember { mutableStateOf(false) }
    var subLangs by remember { mutableStateOf("") }
    var embedSubs by remember { mutableStateOf(false) }

    // ── Advanced state ────────────────────────────────────────────────────────
    var concurrentFragments by remember { mutableStateOf("5") }
    var socketTimeout by remember { mutableStateOf("30") }
    var youtubePlayerClient by remember { mutableStateOf("ios") }
    var extractorArgs by remember { mutableStateOf("") }
    var sponsorBlockRemove by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }

    // ── UI collapse state ─────────────────────────────────────────────────────
    // rememberSaveable: Voyager restores these across tab switches
    var subsExpanded: Boolean by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded: Boolean by rememberSaveable { mutableStateOf(false) }
    var cookieHintExpanded: Boolean by rememberSaveable { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            try {
                isLoading = ytDlpStatus == null
                ytDlpStatus = client.getYtDlpStatus()

                val settings = client.getSettings()
                val ytDlp = settings.ytDlp

                // Cookies source priority: browser > content (if previously set) > file
                cookiesFromBrowser = ytDlp.cookiesFromBrowser ?: ""
                cookiesContent = ytDlp.cookiesContent ?: ""
                cookiesFile = ytDlp.cookiesFile ?: ""
                cookiesSource = when {
                    ytDlp.cookiesFromBrowser?.isNotBlank() == true -> CookiesSource.BROWSER
                    ytDlp.cookiesFile?.isNotBlank() == true        -> CookiesSource.FILE
                    else                                           -> CookiesSource.BROWSER
                }

                // SSL
                legacyServerConnect = ytDlp.legacyServerConnect
                noCheckCertificate = ytDlp.noCheckCertificate

                // Formats
                preferredFormats = ytDlp.preferredFormats ?: ""
                formatSort = ytDlp.formatSort ?: ""
                checkFormats = ytDlp.checkFormats
                mergeOutputFormat = ytDlp.mergeOutputFormat ?: ""

                // Rate limiting
                rateLimit = ytDlp.rateLimit ?: ""
                sleepInterval = ytDlp.sleepInterval?.toString() ?: ""
                maxSleepInterval = ytDlp.maxSleepInterval?.toString() ?: ""

                // Subtitles
                writeSubs = ytDlp.writeSubs
                writeAutoSubs = ytDlp.writeAutoSubs
                subLangs = ytDlp.subLangs ?: ""
                embedSubs = ytDlp.embedSubs

                // Advanced
                concurrentFragments = ytDlp.concurrentFragments.toString()
                socketTimeout = ytDlp.socketTimeout.toString()
                youtubePlayerClient = ytDlp.youtubePlayerClient
                extractorArgs = ytDlp.extractorArgs ?: ""
                sponsorBlockRemove = ytDlp.sponsorBlockRemove ?: ""
                userAgent = ytDlp.userAgent ?: ""

                // Proxy
                proxyEnabled = settings.proxy.enabled
                proxyType = settings.proxy.type
                proxyHost = settings.proxy.host
                proxyPort = settings.proxy.port.toString()
                proxyUsername = settings.proxy.username ?: ""
                proxyPassword = "" // masked on server

                // Auto-expand collapsible sections if they have non-default values
                // Only expand if currently collapsed — respect user's manual collapse
                if (!subsExpanded) {
                    subsExpanded = writeSubs || writeAutoSubs || subLangs.isNotBlank() || embedSubs
                }
                if (!advancedExpanded) {
                    advancedExpanded = rateLimit.isNotBlank()
                        || sleepInterval.isNotBlank()
                        || maxSleepInterval.isNotBlank()
                        || (concurrentFragments.toIntOrNull() ?: 5) != 5
                        || (socketTimeout.toIntOrNull() ?: 30) != 30
                        || youtubePlayerClient != "ios"
                        || extractorArgs.isNotBlank()
                        || sponsorBlockRemove.isNotBlank()
                        || userAgent.isNotBlank()
                }

                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load settings"
            } finally {
                isLoading = false
            }
        }
    }

    fun saveSettings() {
        scope.launch {
            isSaving = true
            successMessage = null
            errorMessage = null
            try {
                val request = SystemSettingsDto(
                    ytDlp = YtDlpSettingsDto(
                        // Cookies — send only the active source
                        cookiesFromBrowser = if (cookiesSource == CookiesSource.BROWSER) cookiesFromBrowser.takeIf { it.isNotBlank() } else null,
                        cookiesContent     = if (cookiesSource == CookiesSource.TEXT)    cookiesContent.takeIf { it.isNotBlank() } else null,
                        cookiesFile        = if (cookiesSource == CookiesSource.FILE)    cookiesFile.takeIf { it.isNotBlank() } else null,
                        // SSL
                        legacyServerConnect = legacyServerConnect,
                        noCheckCertificate  = noCheckCertificate,
                        // Formats
                        preferredFormats  = preferredFormats.takeIf { it.isNotBlank() },
                        formatSort        = formatSort.takeIf { it.isNotBlank() },
                        checkFormats      = checkFormats,
                        mergeOutputFormat = mergeOutputFormat.takeIf { it.isNotBlank() },
                        // Rate limiting
                        rateLimit        = rateLimit.takeIf { it.isNotBlank() },
                        sleepInterval    = sleepInterval.toIntOrNull(),
                        maxSleepInterval = maxSleepInterval.toIntOrNull(),
                        // Subtitles
                        writeSubs     = writeSubs,
                        writeAutoSubs = writeAutoSubs,
                        subLangs      = subLangs.takeIf { it.isNotBlank() },
                        embedSubs     = embedSubs,
                        // Advanced
                        concurrentFragments = concurrentFragments.toIntOrNull() ?: 5,
                        socketTimeout       = socketTimeout.toIntOrNull() ?: 30,
                        youtubePlayerClient = youtubePlayerClient,
                        extractorArgs       = extractorArgs.takeIf { it.isNotBlank() },
                        sponsorBlockRemove  = sponsorBlockRemove.takeIf { it.isNotBlank() },
                        userAgent           = userAgent.takeIf { it.isNotBlank() },
                    ),
                    proxy = ProxySettingsDto(
                        enabled  = proxyEnabled,
                        type     = proxyType,
                        host     = proxyHost,
                        port     = proxyPort.toIntOrNull() ?: 8080,
                        username = proxyUsername.takeIf { it.isNotBlank() },
                        password = proxyPassword.takeIf { it.isNotBlank() },
                    ),
                )
                client.updateSettings(request)
                successMessage = "Settings saved"
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to save settings"
            } finally {
                isSaving = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        errorMessage?.let {
            ErrorCard(message = it, onRetry = { loadData() })
        }

        successMessage?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // ── Workspace ─────────────────────────────────────────────────────────
        SectionCard(title = "Workspace") {
            workspaceState.selectedWorkspace?.let { ws ->
                InfoRow("Name", ws.name)
                InfoRow("Slug", ws.slug)
                InfoRow("Role", ws.role)
            } ?: Text("No workspace selected", style = MaterialTheme.typography.bodyMedium)
        }

        // ── yt-dlp version ────────────────────────────────────────────────────
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
                                    try { client.updateYtDlp(); loadData() }
                                    catch (e: Exception) { errorMessage = e.message ?: "Update failed" }
                                    finally { isUpdating = false }
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
                        Text("Up to date", style = MaterialTheme.typography.bodyMedium, color = StatusCompleted)
                    }
                }
            }
        }

        // ── Cookies ───────────────────────────────────────────────────────────
        SectionCard(title = "Cookies") {
            Text(
                "Required for age-restricted, private, or members-only content.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cookie source selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = cookiesSource == CookiesSource.BROWSER,
                    onClick = { cookiesSource = CookiesSource.BROWSER },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) { Text("Browser") }
                SegmentedButton(
                    selected = cookiesSource == CookiesSource.TEXT,
                    onClick = { cookiesSource = CookiesSource.TEXT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) { Text("Paste Text") }
                SegmentedButton(
                    selected = cookiesSource == CookiesSource.FILE,
                    onClick = { cookiesSource = CookiesSource.FILE },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) { Text("File Path") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (cookiesSource) {
                CookiesSource.BROWSER -> {
                    Text(
                        "yt-dlp will read cookies directly from your browser's profile on the server machine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    var browserExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = browserExpanded,
                        onExpandedChange = { browserExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = cookiesFromBrowser.ifBlank { "None" },
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
                                    onClick = { cookiesFromBrowser = browser; browserExpanded = false },
                                )
                            }
                        }
                    }
                }

                CookiesSource.TEXT -> {
                    TextButton(
                        onClick = { cookieHintExpanded = !cookieHintExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            if (cookieHintExpanded) "Hide instructions" else "How to get cookies from browser",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    if (cookieHintExpanded) {
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = cookiesContent,
                        onValueChange = { cookiesContent = it },
                        label = { Text("Cookies (Netscape format)") },
                        placeholder = { Text("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\t...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        maxLines = 15,
                    )

                    if (cookiesContent.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { cookiesContent = "" }) {
                            Text("Clear cookies", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                CookiesSource.FILE -> {
                    Text(
                        "Path to a Netscape-format cookies.txt file on the server machine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookiesFile,
                        onValueChange = { cookiesFile = it },
                        label = { Text("Cookies File Path") },
                        placeholder = { Text("/path/to/cookies.txt") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        // ── SSL ───────────────────────────────────────────────────────────────
        SectionCard(title = "SSL / TLS") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Legacy server connect", style = MaterialTheme.typography.bodyMedium)
                    Text("Fix SSL errors on some sites (e.g. RuTube)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = legacyServerConnect, onCheckedChange = { legacyServerConnect = it })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("No check certificate", style = MaterialTheme.typography.bodyMedium)
                    Text("Disable TLS validation — use with caution!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Switch(checked = noCheckCertificate, onCheckedChange = { noCheckCertificate = it })
            }
        }

        // ── Formats ───────────────────────────────────────────────────────────
        SectionCard(title = "Format & Quality") {
            Text(
                "Override automatic format selection. Leave empty to use the quality selected per job.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = preferredFormats,
                onValueChange = { preferredFormats = it },
                label = { Text("Format selector (-f)") },
                placeholder = { Text("bestvideo[height<=1080]+bestaudio/best") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("yt-dlp format string. Overrides per-job quality if set.") },
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = formatSort,
                onValueChange = { formatSort = it },
                label = { Text("Format sort (-S)") },
                placeholder = { Text("res,tbr,fps") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Used when Format selector is empty. Example: res:1080,tbr,fps") },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check formats", style = MaterialTheme.typography.bodyMedium)
                    Text("Verify format availability before download (may fail on some sites)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checkFormats, onCheckedChange = { checkFormats = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Merge output format dropdown
            var mergeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = mergeExpanded, onExpandedChange = { mergeExpanded = it }) {
                OutlinedTextField(
                    value = mergeOutputFormat.ifBlank { "Auto" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Merge output format") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mergeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Container for muxed video+audio. Auto = yt-dlp decides.") },
                )
                ExposedDropdownMenu(expanded = mergeExpanded, onDismissRequest = { mergeExpanded = false }) {
                    mergeOutputFormats.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text(fmt.ifBlank { "Auto" }) },
                            onClick = { mergeOutputFormat = fmt; mergeExpanded = false },
                        )
                    }
                }
            }
        }

        // ── Subtitles (collapsible) ────────────────────────────────────────────
        SectionCard(title = "Subtitles") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Subtitles settings", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { subsExpanded = !subsExpanded }) {
                    Text(if (subsExpanded) "Collapse" else "Expand")
                }
            }

            if (subsExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Download subtitles", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = writeSubs, onCheckedChange = { writeSubs = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-generated subtitles", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = writeAutoSubs, onCheckedChange = { writeAutoSubs = it })
                }
                if (writeSubs || writeAutoSubs) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subLangs,
                        onValueChange = { subLangs = it },
                        label = { Text("Subtitle languages") },
                        placeholder = { Text("ru,en") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Comma-separated language codes") },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Embed subtitles", style = MaterialTheme.typography.bodyMedium)
                            Text("Requires ffmpeg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = embedSubs, onCheckedChange = { embedSubs = it })
                    }
                }
            }
        }

        // ── Proxy ─────────────────────────────────────────────────────────────
        SectionCard(title = "Proxy") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Proxy", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
            }

            if (proxyEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = proxyType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        proxyTypes.forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { proxyType = type; typeExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = proxyUsername, onValueChange = { proxyUsername = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = proxyPassword, onValueChange = { proxyPassword = it }, label = { Text("Password") }, placeholder = { Text("unchanged") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        }

        // ── Advanced (collapsible) ────────────────────────────────────────────
        SectionCard(title = "Advanced yt-dlp Settings") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Rate limiting, performance, site-specific", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Text(if (advancedExpanded) "Collapse" else "Expand")
                }
            }

            if (advancedExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rate Limiting", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = rateLimit,
                    onValueChange = { rateLimit = it },
                    label = { Text("Rate limit") },
                    placeholder = { Text("5M") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Max download speed, e.g. 5M, 500K. Empty = unlimited.") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sleepInterval,
                        onValueChange = { sleepInterval = it },
                        label = { Text("Sleep interval (s)") },
                        placeholder = { Text("2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("Pause between requests") },
                    )
                    OutlinedTextField(
                        value = maxSleepInterval,
                        onValueChange = { maxSleepInterval = it },
                        label = { Text("Max sleep (s)") },
                        placeholder = { Text("5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Performance", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = concurrentFragments,
                        onValueChange = { concurrentFragments = it },
                        label = { Text("Concurrent fragments") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("Default: 5") },
                    )
                    OutlinedTextField(
                        value = socketTimeout,
                        onValueChange = { socketTimeout = it },
                        label = { Text("Socket timeout (s)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = { Text("Default: 30") },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Site-specific", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))

                // YouTube player client dropdown
                var ytClientExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = ytClientExpanded, onExpandedChange = { ytClientExpanded = it }) {
                    OutlinedTextField(
                        value = youtubePlayerClient.ifBlank { "yt-dlp default (web)" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("YouTube Player Client") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ytClientExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                when (youtubePlayerClient) {
                                    "ios"         -> "No JS runtime (deno) needed"
                                    "android"     -> "No JS runtime (deno) needed"
                                    "web"         -> "Requires deno installed on server"
                                    "mweb"        -> "No JS runtime needed, lower quality"
                                    "tv_embedded" -> "No JS runtime needed"
                                    ""            -> "yt-dlp default (web) — requires deno"
                                    else          -> ""
                                }
                            )
                        },
                    )
                    ExposedDropdownMenu(expanded = ytClientExpanded, onDismissRequest = { ytClientExpanded = false }) {
                        youtubePlayerClients.forEach { client ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (client) {
                                            "ios"         -> "ios  (recommended, no deno needed)"
                                            "android"     -> "android  (no deno needed)"
                                            "web"         -> "web  (most formats, requires deno)"
                                            "mweb"        -> "mweb  (mobile, no deno)"
                                            "tv_embedded" -> "tv_embedded  (no deno)"
                                            ""            -> "yt-dlp default (web, requires deno)"
                                            else          -> client
                                        }
                                    )
                                },
                                onClick = { youtubePlayerClient = client; ytClientExpanded = false },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = extractorArgs,
                    onValueChange = { extractorArgs = it },
                    label = { Text("Extractor args (advanced)") },
                    placeholder = { Text("vk:nocheckcertificate=1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "--extractor-args for non-YouTube extractors. " +
                            "For YouTube player client use the dropdown above. " +
                            "If this field contains 'player_client', it takes full priority."
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sponsorBlockRemove,
                    onValueChange = { sponsorBlockRemove = it },
                    label = { Text("SponsorBlock remove") },
                    placeholder = { Text("sponsor,selfpromo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Comma-separated categories to cut from video") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent") },
                    placeholder = { Text("Mozilla/5.0 ...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        // ── Save ──────────────────────────────────────────────────────────────
        Button(
            onClick = { saveSettings() },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isSaving) "Saving..." else "Save Settings")
        }

        // ── About ─────────────────────────────────────────────────────────────
        SectionCard(title = "About") {
            InfoRow("App", "TG Video Downloader")
            InfoRow("Version", BuildConfig.APP_VERSION)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
