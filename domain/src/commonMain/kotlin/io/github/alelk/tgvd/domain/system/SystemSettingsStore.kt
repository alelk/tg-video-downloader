package io.github.alelk.tgvd.domain.system

/**
 * Port: the current [SystemSettings] of the server. Reads come from memory; [save] persists the new
 * settings so they survive a restart and makes them current at once.
 */
interface SystemSettingsStore {
    /** The settings in effect now. */
    fun current(): SystemSettings

    /** Whether the administrator allows updating the yt-dlp binary from the API (a deployment setting). */
    fun isYtDlpUpdateAllowed(): Boolean

    /** Replaces the current settings with [settings] and persists them. */
    suspend fun save(settings: SystemSettings)
}
