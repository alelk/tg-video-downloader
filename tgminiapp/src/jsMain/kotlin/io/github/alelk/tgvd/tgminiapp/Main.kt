package io.github.alelk.tgvd.tgminiapp

import androidx.compose.runtime.remember
import com.kirillNay.telegram.miniapp.compose.telegramWebApp
import com.kirillNay.telegram.miniapp.webApp.webApp
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClientImpl
import io.github.alelk.tgvd.api.contract.common.apiJson
import io.github.alelk.tgvd.features.app.TgvdApp
import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage
import io.github.alelk.tgvd.features.di.featuresModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.compose.KoinApplication
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
            val telegramColors = remember(telegramStyle.colors) { telegramStyle.colors.toThemeColors() }
            // No remember: startParam is read every time the Mini App is opened
            val platformCallbacks = createPlatformCallbacks()
            val isDark = remember(telegramColors) { detectIsDarkTheme(telegramColors) }

            TgvdApp(
                isDarkTheme = isDark,
                telegramColors = telegramColors,
                platformCallbacks = platformCallbacks,
            )
        }
    }
}

private fun createPlatformModule() = module {
    single<PreferencesStorage> { LocalStoragePreferences() }
}

private fun createApiModule() = module {
    single<TgVideoDownloaderClient> {
        val httpClient =
            HttpClient(Js) {
                install(ContentNegotiation) { json(apiJson) }
            }
        val baseUrl =
            readEnvConfig("API_BASE_URL")
                ?: js("window.location.origin").unsafeCast<String>()
        TgVideoDownloaderClientImpl(
            httpClient = httpClient,
            baseUrl = baseUrl,
            // Read on every request: Telegram may refresh initData
            initDataProvider = ::currentInitData,
        )
    }
}

private fun currentInitData(): String = try {
    webApp.rawInitData.takeIf { it.isNotBlank() } ?: "dev"
} catch (_: Throwable) {
    "dev"
}

/** Read a value from window.__ENV__ (set by config.js, generated at runtime in Docker). */
private fun readEnvConfig(key: String): String? = try {
    val env = js("window.__ENV__")
    val value = env[key]
    (value as? String)?.takeIf { it.isNotBlank() }
} catch (_: Throwable) {
    null
}
