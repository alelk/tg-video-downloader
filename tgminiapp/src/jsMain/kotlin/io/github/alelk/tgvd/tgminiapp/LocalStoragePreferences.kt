package io.github.alelk.tgvd.tgminiapp

import io.github.alelk.tgvd.features.common.persistence.PreferencesStorage
import kotlinx.browser.window

/**
 * JS implementation of [PreferencesStorage] using browser `localStorage`.
 * Data persists across page reloads and app restarts.
 */
class LocalStoragePreferences : PreferencesStorage {
    override fun get(key: String): String? = window.localStorage.getItem(key)
    override fun set(key: String, value: String) { window.localStorage.setItem(key, value) }
    override fun remove(key: String) { window.localStorage.removeItem(key) }
}

