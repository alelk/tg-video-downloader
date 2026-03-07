package io.github.alelk.tgvd.features.settings.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    // Settings state
    var cookiesFromBrowser by remember { mutableStateOf("") }
    var cookiesFile by remember { mutableStateOf("") }
    var proxyEnabled by remember { mutableStateOf(false) }
    var proxyType by remember { mutableStateOf("HTTP") }
    var proxyHost by remember { mutableStateOf("127.0.0.1") }
    var proxyPort by remember { mutableStateOf("8080") }
    var proxyUsername by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            try {
                isLoading = ytDlpStatus == null
                ytDlpStatus = client.getYtDlpStatus()

                val settings = client.getSettings()
                cookiesFromBrowser = settings.ytDlp.cookiesFromBrowser ?: ""
                cookiesFile = settings.ytDlp.cookiesFile ?: ""
                proxyEnabled = settings.proxy.enabled
                proxyType = settings.proxy.type
                proxyHost = settings.proxy.host
                proxyPort = settings.proxy.port.toString()
                proxyUsername = settings.proxy.username ?: ""
                proxyPassword = ""  // masked on server

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
                        cookiesFromBrowser = cookiesFromBrowser.takeIf { it.isNotBlank() },
                        cookiesFile = cookiesFile.takeIf { it.isNotBlank() },
                    ),
                    proxy = ProxySettingsDto(
                        enabled = proxyEnabled,
                        type = proxyType,
                        host = proxyHost,
                        port = proxyPort.toIntOrNull() ?: 8080,
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
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Workspace info
        SectionCard(title = "Workspace") {
            workspaceState.selectedWorkspace?.let { ws ->
                InfoRow("Name", ws.name)
                InfoRow("Slug", ws.slug)
                InfoRow("Role", ws.role)
            } ?: Text("No workspace selected", style = MaterialTheme.typography.bodyMedium)
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
                                        loadData()
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

        // Cookies section
        SectionCard(title = "YouTube Cookies") {
            Text(
                "Required for age-restricted, private, or members-only content. " +
                    "Use a private/incognito window to export cookies for best results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Browser dropdown
            var browserExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = browserExpanded,
                onExpandedChange = { browserExpanded = it },
            ) {
                OutlinedTextField(
                    value = cookiesFromBrowser.ifBlank { "None" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cookies from Browser") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(browserExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = browserExpanded,
                    onDismissRequest = { browserExpanded = false },
                ) {
                    browserOptions.forEach { browser ->
                        DropdownMenuItem(
                            text = { Text(browser.ifBlank { "None" }) },
                            onClick = {
                                cookiesFromBrowser = browser
                                browserExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("— or —",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
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

        // Proxy section
        SectionCard(title = "Proxy") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enable Proxy", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = proxyEnabled,
                    onCheckedChange = { proxyEnabled = it },
                )
            }

            if (proxyEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                // Proxy type
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = proxyType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        proxyTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    proxyType = type
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { proxyPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proxyUsername,
                        onValueChange = { proxyUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = proxyPassword,
                        onValueChange = { proxyPassword = it },
                        label = { Text("Password") },
                        placeholder = { Text("unchanged") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Save button
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

        // About section
        SectionCard(title = "About") {
            InfoRow("App", "TG Video Downloader")
            InfoRow("Version", BuildConfig.APP_VERSION)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
