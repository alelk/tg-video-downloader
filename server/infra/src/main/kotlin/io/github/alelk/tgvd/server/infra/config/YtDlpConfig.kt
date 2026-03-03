package io.github.alelk.tgvd.server.infra.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class YtDlpConfig(
    val path: String = "yt-dlp",
    val timeout: Duration = 30.minutes,
    val retries: Int = 3,
    val fragmentRetries: Int = 10,
    val allowUpdate: Boolean = true,
    val updateChannel: String = "stable",
    val autoDownload: Boolean = true,
    /** Browser name for --cookies-from-browser (e.g. "chrome", "firefox", "safari", "brave") */
    val cookiesFromBrowser: String? = null,
    /** Path to Netscape-format cookies file for --cookies */
    val cookiesFile: String? = null,
)

