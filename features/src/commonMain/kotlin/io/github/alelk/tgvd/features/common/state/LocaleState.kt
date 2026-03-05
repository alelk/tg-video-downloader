package io.github.alelk.tgvd.features.common.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage

/**
 * Holds the user-selected locale. Persists choice via [PreferencesStorage].
 * Supported locales: "en", "ru". Default: "en".
 */
class LocaleState(
    private val preferences: PreferencesStorage? = null,
) {
    var currentLocale by mutableStateOf(preferences?.get(PREF_KEY) ?: DEFAULT_LOCALE)
        private set

    fun setLocale(locale: String) {
        currentLocale = locale
        preferences?.set(PREF_KEY, locale)
    }

    companion object {
        private const val PREF_KEY = "selected_locale"
        const val DEFAULT_LOCALE = "en"
        val SUPPORTED_LOCALES = listOf("en", "ru")

        fun labelFor(locale: String): String = when (locale) {
            "en" -> "English"
            "ru" -> "Русский"
            else -> locale
        }

        /** Short label used as a locale-switcher icon (renders correctly on all platforms). */
        fun flagFor(locale: String): String = when (locale) {
            "en" -> "EN"
            "ru" -> "RU"
            else -> locale.uppercase().take(2)
        }
    }
}

