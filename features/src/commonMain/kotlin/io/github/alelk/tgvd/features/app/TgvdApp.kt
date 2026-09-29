package io.github.alelk.tgvd.features.app

import androidx.compose.runtime.Composable
import io.github.alelk.tgvd.features.common.theme.PlatformCallbacks
import io.github.alelk.tgvd.features.common.theme.TelegramThemeColors
import io.github.alelk.tgvd.features.common.theme.TgvdTheme
import io.github.alelk.tgvd.features.navigation.AppNavigation

/**
 * The whole app UI — the one composable a platform shell renders (inside its Koin context).
 * The shell supplies only what comes from the platform: theme colors and [PlatformCallbacks].
 */
@Composable
fun TgvdApp(isDarkTheme: Boolean, telegramColors: TelegramThemeColors?, platformCallbacks: PlatformCallbacks) {
    TgvdTheme(
        isDarkTheme = isDarkTheme,
        telegramColors = telegramColors,
        platformCallbacks = platformCallbacks,
    ) {
        WorkspaceGate {
            AppNavigation()
        }
    }
}
