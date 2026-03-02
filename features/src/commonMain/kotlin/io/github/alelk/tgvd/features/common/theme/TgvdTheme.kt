package io.github.alelk.tgvd.features.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Telegram theme colors provided by the Telegram WebApp JS API.
 * Nullable fields indicate the value wasn't provided by the platform.
 */
data class TelegramThemeColors(
    val bgColor: Color? = null,
    val textColor: Color? = null,
    val hintColor: Color? = null,
    val buttonColor: Color? = null,
    val buttonTextColor: Color? = null,
    val linkColor: Color? = null,
    val secondaryBgColor: Color? = null,
)

/**
 * Platform callbacks for Telegram-specific features.
 * Allows features module to trigger platform actions without JS dependency.
 */
data class PlatformCallbacks(
    val onHapticFeedback: (() -> Unit)? = null,
    val onShowBackButton: ((Boolean) -> Unit)? = null,
    val onShowMainButton: ((text: String, onClick: () -> Unit) -> Unit)? = null,
    val onHideMainButton: (() -> Unit)? = null,
)

val LocalPlatformCallbacks = staticCompositionLocalOf { PlatformCallbacks() }

private val DarkColorScheme = darkColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    primaryContainer = Blue700,
    onPrimaryContainer = Color.White,
    secondary = Blue600,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color(0xFFE0E0E0),
    surface = DarkSurface,
    onSurface = Color(0xFFE0E0E0),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    onSurfaceVariant = Color(0xFF9E9EAE),
    error = StatusFailed,
    errorContainer = Color(0xFF3D1C1C),
    onErrorContainer = Color(0xFFFFB4AB),
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEFF),
    onPrimaryContainer = Blue700,
    secondary = Blue500,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF6B6B7B),
    error = StatusFailed,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

private fun buildColorScheme(
    isDark: Boolean,
    telegramColors: TelegramThemeColors?,
): ColorScheme {
    val base = if (isDark) DarkColorScheme else LightColorScheme
    val tg = telegramColors ?: return base
    return base.copy(
        primary = tg.buttonColor ?: base.primary,
        onPrimary = tg.buttonTextColor ?: base.onPrimary,
        background = tg.bgColor ?: base.background,
        onBackground = tg.textColor ?: base.onBackground,
        surface = tg.bgColor ?: base.surface,
        onSurface = tg.textColor ?: base.onSurface,
        surfaceContainer = tg.secondaryBgColor ?: base.surfaceContainer,
        onSurfaceVariant = tg.hintColor ?: base.onSurfaceVariant,
    )
}

@Composable
fun TgvdTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    telegramColors: TelegramThemeColors? = null,
    platformCallbacks: PlatformCallbacks = PlatformCallbacks(),
    content: @Composable () -> Unit,
) {
    val colorScheme = buildColorScheme(isDarkTheme, telegramColors)

    CompositionLocalProvider(LocalPlatformCallbacks provides platformCallbacks) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

