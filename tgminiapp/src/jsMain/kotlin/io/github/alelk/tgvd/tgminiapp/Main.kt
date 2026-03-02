package io.github.alelk.tgvd.tgminiapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClientImpl
import io.github.alelk.tgvd.api.contract.common.apiJson
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
import kotlinx.browser.document
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.koin.compose.KoinApplication
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initTelegramWebApp()

    val root = document.getElementById("root")!!
    ComposeViewport(root) {
        val apiModule = remember { createApiModule() }

        KoinApplication(application = {
            modules(apiModule, featuresModule)
        }) {
            val telegramColors = remember { readTelegramThemeColors() }
            val platformCallbacks = remember { createPlatformCallbacks() }

            TgvdTheme(
                isDarkTheme = true, // Telegram dark mode is more common
                telegramColors = telegramColors,
                platformCallbacks = platformCallbacks,
            ) {
                AppNavigation()
            }
        }
    }
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
        val baseUrl = js("window.location.origin").unsafeCast<String>()
        val initDataProvider = {
            try {
                js("window.Telegram.WebApp.initData").unsafeCast<String>()
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
)

private fun parseColor(hex: dynamic): Color? {
    val str = hex as? String ?: return null
    if (!str.startsWith("#") || str.length != 7) return null
    val r = str.substring(1, 3).toIntOrNull(16) ?: return null
    val g = str.substring(3, 5).toIntOrNull(16) ?: return null
    val b = str.substring(5, 7).toIntOrNull(16) ?: return null
    return Color(r, g, b)
}
