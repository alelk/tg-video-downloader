package io.github.alelk.tgvd.tgminiapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import io.github.alelk.tgvd.api.client.ApiException
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClientImpl
import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage
import io.github.alelk.tgvd.features.common.state.WorkspaceState
import io.github.alelk.tgvd.features.common.theme.PlatformCallbacks
import io.github.alelk.tgvd.features.common.theme.TelegramThemeColors
import io.github.alelk.tgvd.features.common.theme.TgvdTheme
import io.github.alelk.tgvd.features.di.featuresModule
import io.github.alelk.tgvd.features.navigation.AppNavigation
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.document
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initTelegramWebApp()

    val root = document.getElementById("root")!!
    ComposeViewport(root) {
        val apiModule = remember { createApiModule() }
        val platformModule = remember { createPlatformModule() }

        KoinApplication(application = {
            modules(platformModule, apiModule, featuresModule)
        }) {
            val telegramColors = remember { readTelegramThemeColors() }
            val platformCallbacks = remember { createPlatformCallbacks() }
            val isDark = remember(telegramColors) { detectIsDarkTheme(telegramColors) }

            TgvdTheme(
                isDarkTheme = isDark,
                telegramColors = telegramColors,
                platformCallbacks = platformCallbacks,
            ) {
                WorkspaceInitializer {
                    AppNavigation()
                }
            }
        }
    }
}

private fun createPlatformModule() = module {
    single<PreferencesStorage> { LocalStoragePreferences() }
}

private fun initTelegramWebApp() {
    try {
        js("window.Telegram.WebApp.ready()")
        js("window.Telegram.WebApp.expand()")
    } catch (_: Throwable) {
        // Not running inside Telegram — dev mode
    }
}

private fun createApiModule() = module {
    single<TgVideoDownloaderClient> {
        val httpClient = HttpClient(Js) {
            install(ContentNegotiation) { json(apiJson) }
        }
        val baseUrl = readEnvConfig("API_BASE_URL")
            ?: js("window.location.origin").unsafeCast<String>()
        val initDataProvider = {
            try {
                val initData = js("window.Telegram.WebApp.initData").unsafeCast<String>()
                initData.takeIf { it.isNotBlank() } ?: "dev"
            } catch (_: Throwable) {
                "dev"
            }
        }
        TgVideoDownloaderClientImpl(
            httpClient = httpClient,
            baseUrl = baseUrl,
            initDataProvider = initDataProvider,
        )
    }
}

/** Read a value from window.__ENV__ (set by config.js, generated at runtime in Docker). */
private fun readEnvConfig(key: String): String? = try {
    val env = js("window.__ENV__")
    val value = env[key]
    (value as? String)?.takeIf { it.isNotBlank() }
} catch (_: Throwable) {
    null
}

private fun readTelegramThemeColors(): TelegramThemeColors? = try {
    val tp = js("window.Telegram.WebApp.themeParams")
    TelegramThemeColors(
        bgColor = parseColor(tp.bg_color),
        textColor = parseColor(tp.text_color),
        hintColor = parseColor(tp.hint_color),
        buttonColor = parseColor(tp.button_color),
        buttonTextColor = parseColor(tp.button_text_color),
        linkColor = parseColor(tp.link_color),
        secondaryBgColor = parseColor(tp.secondary_bg_color),
    )
} catch (_: Throwable) {
    null
}

private fun createPlatformCallbacks() = PlatformCallbacks(
    onHapticFeedback = {
        try { js("window.Telegram.WebApp.HapticFeedback.impactOccurred('light')") } catch (_: Throwable) {}
    },
    readTextFromClipboard = { callback ->
        readClipboardText(callback)
    },
)

/**
 * Reads text from clipboard using Telegram WebApp API (preferred on iOS)
 * with fallback to navigator.clipboard Web API.
 *
 * On iOS in Telegram Mini App, native paste doesn't work with Compose Canvas-based
 * text fields, so we use the Telegram `readTextFromClipboard` method instead.
 */
private fun readClipboardText(callback: (String?) -> Unit) {
    val jsCallback: (dynamic) -> Unit = { text -> callback(text as? String) }

    // Try Telegram WebApp API first (works on iOS in Telegram)
    try {
        val webApp: dynamic = js("window.Telegram.WebApp")
        if (webApp.readTextFromClipboard != null && webApp.readTextFromClipboard != undefined) {
            webApp.readTextFromClipboard(jsCallback)
            return
        }
    } catch (_: Throwable) {}

    // Fallback: Web Clipboard API (works in desktop browsers)
    try {
        val clipboard: dynamic = js("navigator.clipboard")
        if (clipboard != null && clipboard != undefined) {
            clipboard.readText().then(jsCallback).catch { _: dynamic -> callback(null) }
            return
        }
    } catch (_: Throwable) {}

    callback(null)
}

private fun parseColor(hex: dynamic): Color? {
    val str = hex as? String ?: return null
    if (!str.startsWith("#") || str.length != 7) return null
    val r = str.substring(1, 3).toIntOrNull(16) ?: return null
    val g = str.substring(3, 5).toIntOrNull(16) ?: return null
    val b = str.substring(5, 7).toIntOrNull(16) ?: return null
    return Color(r, g, b)
}

/**
 * Determines whether to use dark theme based on Telegram's bgColor luminance.
 * Falls back to dark theme when Telegram colors are not available (dev mode).
 * Uses W3C relative luminance formula: dark if luminance < 0.5.
 */
private fun detectIsDarkTheme(telegramColors: TelegramThemeColors?): Boolean {
    val bg = telegramColors?.bgColor ?: return true
    // sRGB relative luminance (simplified)
    val luminance = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return luminance < 0.5f
}

/**
 * Initializes workspace before rendering the main UI.
 * Fetches user's workspaces; creates one if none exist.
 * Restores previously selected workspace from localStorage.
 * Sets the workspaceSlug on the client so all subsequent API calls are scoped correctly.
 */
@Composable
private fun WorkspaceInitializer(content: @Composable () -> Unit) {
    val client = koinInject<TgVideoDownloaderClient>()
    val workspaceState = koinInject<WorkspaceState>()
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(retryTrigger) {
        error = null
        ready = false
        try {
            val workspaces = client.getWorkspaces()
            val ws = if (workspaces.items.isNotEmpty()) {
                workspaces.items
            } else {
                // POST /workspaces with getOrCreate semantics on the server:
                // if slug "default" already exists the server returns 200 with existing workspace
                val created = client.createWorkspace(CreateWorkspaceRequestDto(slug = "default", name = "Default"))
                listOf(created)
            }
            workspaceState.workspaces = ws

            // Restore previously selected workspace or use first
            val savedSlug = workspaceState.savedSlug
            val selected = savedSlug?.let { slug -> ws.find { it.slug == slug } } ?: ws.first()
            workspaceState.selectWorkspace(selected)

            (client as TgVideoDownloaderClientImpl).workspaceSlug = selected.slug
            ready = true
        } catch (e: ApiException) {
            error = "${e.code}: ${e.message}"
        } catch (e: Exception) {
            error = e.message ?: "Failed to initialize workspace"
        }
    }

    // Sync client workspaceSlug when selection changes
    LaunchedEffect(workspaceState.selectedWorkspace) {
        workspaceState.selectedWorkspace?.let { ws ->
            (client as TgVideoDownloaderClientImpl).workspaceSlug = ws.slug
        }
    }

    when {
        error != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = "⚠️ Initialization Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { retryTrigger++ }) {
                        Text("Retry")
                    }
                }
            }
        }
        ready -> content()
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
