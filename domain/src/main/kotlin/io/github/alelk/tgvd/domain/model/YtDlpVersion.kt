package io.github.alelk.tgvd.domain.model

import kotlinx.datetime.Instant

data class YtDlpVersion(
    val currentVersion: String,
    val latestVersion: String? = null,
    val lastCheckedAt: Instant? = null
) {
    val isUpdateAvailable: Boolean
        get() = latestVersion != null && latestVersion != currentVersion
}
