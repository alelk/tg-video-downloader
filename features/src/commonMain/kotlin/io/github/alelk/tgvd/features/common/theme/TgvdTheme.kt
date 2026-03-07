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
    /**
     * Read text from clipboard using platform-specific API.
     * On Telegram Mini App uses `readTextFromClipboard` Telegram WebApp API (Bot API 6.4+).
     * Falls back to `navigator.clipboard.readText()` on desktop browsers.
     * The callback receives the clipboard text, or null if unavailable.
     */
    val readTextFromClipboard: ((callback: (String?) -> Unit) -> Unit)? = null,
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
    onSurfaceVariant = Color(0xFFB0B0BE),
    outline = Color(0xFF8888A0),
    outlineVariant = Color(0xFF44445A),
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
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = StatusFailed,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

/**
 * Derives a readable secondary text color from Telegram's text color.
 * Telegram's `hint_color` is too faded for Material3 `onSurfaceVariant` role
 * (used for navigation icons, card labels, chip text — must stay readable).
 * Instead we take the main text color and reduce opacity to ~70%.
 */
private fun deriveSecondaryTextColor(textColor: Color?, hintColor: Color?): Color? {
    // Prefer text color at 70% opacity — always readable against its own background
    textColor?.let { return it.copy(alpha = 0.70f) }
    return hintColor
}

private fun buildColorScheme(
    isDark: Boolean,
    telegramColors: TelegramThemeColors?,
): ColorScheme {
    val base = if (isDark) DarkColorScheme else LightColorScheme
    val tg = telegramColors ?: return base

    val cardSurface = tg.secondaryBgColor ?: base.surfaceContainer
    val secondaryText = deriveSecondaryTextColor(tg.textColor, tg.hintColor) ?: base.onSurfaceVariant

    return base.copy(
        // Brand
        primary = tg.buttonColor ?: base.primary,
        onPrimary = tg.buttonTextColor ?: base.onPrimary,

        // Main background & text
        background = tg.bgColor ?: base.background,
        onBackground = tg.textColor ?: base.onBackground,

        // Primary surface (scaffold, screens)
        surface = tg.bgColor ?: base.surface,
        onSurface = tg.textColor ?: base.onSurface,

        // Cards, navigation bar, dialogs, bottom sheets
        surfaceVariant = cardSurface,
        surfaceContainer = cardSurface,
        surfaceContainerLow = tg.bgColor ?: base.surfaceContainerLow,
        surfaceContainerHigh = cardSurface,
        surfaceContainerHighest = cardSurface,

        // Secondary text: icons, labels, chips — must stay readable
        onSurfaceVariant = secondaryText,

        // Borders, dividers
        outline = tg.hintColor ?: base.outline,
        outlineVariant = tg.hintColor?.copy(alpha = 0.40f) ?: base.outlineVariant,
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

