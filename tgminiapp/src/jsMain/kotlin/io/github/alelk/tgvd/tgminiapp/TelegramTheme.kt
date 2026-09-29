package io.github.alelk.tgvd.tgminiapp

import com.kirillNay.telegram.miniapp.compose.TelegramColors
import io.github.alelk.tgvd.features.common.theme.TelegramThemeColors

/** Telegram `themeParams` (via the tg-mini-app library) → the colors the features theme understands. */
internal fun TelegramColors.toThemeColors(): TelegramThemeColors = TelegramThemeColors(
    bgColor = backgroundColor,
    textColor = textColor,
    hintColor = hintColor,
    buttonColor = buttonColor,
    buttonTextColor = buttonTextColor,
    linkColor = linkColor,
    secondaryBgColor = secondaryBackgroundColor,
)

/**
 * Determines whether to use dark theme based on Telegram's bgColor luminance.
 * Falls back to dark theme when Telegram colors are not available (dev mode).
 * Uses W3C relative luminance formula: dark if luminance < 0.5.
 */
internal fun detectIsDarkTheme(telegramColors: TelegramThemeColors?): Boolean {
    val bg = telegramColors?.bgColor ?: return true
    // sRGB relative luminance (simplified)
    val luminance = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return luminance < 0.5f
}
