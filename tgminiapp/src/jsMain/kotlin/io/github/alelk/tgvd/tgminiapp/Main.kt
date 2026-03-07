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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kirillNay.telegram.miniapp.webApp.webApp
import com.kirillNay.telegram.miniapp.compose.telegramWebApp
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
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

fun main() {
    webApp.ready()
    webApp.expand()

    telegramWebApp { telegramStyle ->
        val apiModule = remember { createApiModule() }
        val platformModule = remember { createPlatformModule() }

        KoinApplication(application = {
            modules(platformModule, apiModule, featuresModule)
        }) {
            val telegramColors = remember(telegramStyle.colors) {
                TelegramThemeColors(
                    bgColor = telegramStyle.colors.backgroundColor,
                    textColor = telegramStyle.colors.textColor,
                    hintColor = telegramStyle.colors.hintColor,
                    buttonColor = telegramStyle.colors.buttonColor,
                    buttonTextColor = telegramStyle.colors.buttonTextColor,
                    linkColor = telegramStyle.colors.linkColor,
                    secondaryBgColor = telegramStyle.colors.secondaryBackgroundColor,
                )
            }
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

private fun createApiModule() = module {
    single<TgVideoDownloaderClient> {
        val httpClient = HttpClient(Js) {
            install(ContentNegotiation) { json(apiJson) }
        }
        val baseUrl = readEnvConfig("API_BASE_URL")
            ?: js("window.location.origin").unsafeCast<String>()
        val initDataProvider = {
            try {
                webApp.rawInitData.takeIf { it.isNotBlank() } ?: "dev"
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

private fun createPlatformCallbacks() = PlatformCallbacks(
    onHapticFeedback = {
        try { webApp.hapticFeedback.impactOccurred("light") } catch (_: Throwable) {}
    },
    readTextFromClipboard = { callback ->
        readClipboardText(callback)
    },
)

/**
 * Reads text from clipboard using Telegram WebApp typed API (preferred on iOS).
 * Falls back to navigator.clipboard Web API for desktop browsers.
 *
 * Note: Telegram's readTextFromClipboard can only be called in response to user interaction
 * (e.g. a click) and requires Bot API 6.4+.
 */
private fun readClipboardText(callback: (String?) -> Unit) {
    // In Telegram Mini App, prefer Telegram API only.
    // navigator.clipboard is often blocked in WebView and throws NotAllowedError.
    val telegramWebApp: dynamic = try {
        val telegram: dynamic = js("window.Telegram")
        telegram?.WebApp
    } catch (_: Throwable) {
        null
    }

    if (telegramWebApp != null && telegramWebApp.readTextFromClipboard != null) {
        try {
            telegramWebApp.readTextFromClipboard { text: dynamic ->
                val value = (text as? String)?.trim()
                callback(value?.takeIf { it.isNotBlank() })
            }
            return
        } catch (_: Throwable) {
            callback(null)
            return
        }
    }

    // Non-Telegram fallback: regular browsers/dev mode.
    try {
        val clipboard: dynamic = js("navigator.clipboard")
        if (clipboard != null && clipboard != undefined) {
            clipboard.readText()
                .then { text: dynamic ->
                    val value = (text as? String)?.trim()
                    callback(value?.takeIf { it.isNotBlank() })
                }
                .catch { _: dynamic -> callback(null) }
            return
        }
    } catch (_: Throwable) {}

    callback(null)
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
