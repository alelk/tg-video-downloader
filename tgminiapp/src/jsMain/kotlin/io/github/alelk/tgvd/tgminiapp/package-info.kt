package io.github.alelk.tgvd.tgminiapp
/**
 * Telegram Mini App module — thin JS shell.
 *
 * Responsibilities:
 * - Initialize Koin DI with API client and features modules
 * - Bridge Telegram WebApp JS API (theme, haptic feedback, back button)
 * - Launch Compose UI from the `:features` module
 *
 * All UI components, screens, and shared logic live in `:features` (KMP commonMain).
 */
