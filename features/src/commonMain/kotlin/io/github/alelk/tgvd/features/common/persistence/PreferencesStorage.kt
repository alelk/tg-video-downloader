package io.github.alelk.tgvd.features.common.persistence

/**
 * Simple key-value storage for persisting user preferences across sessions.
 *
 * Platform-specific implementations:
 * - JS: `localStorage`
 * - JVM/Android: `SharedPreferences` or file-based
 */
interface PreferencesStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

