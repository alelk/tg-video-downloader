package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class YtDlpStatusDto(
    val currentVersion: String,
    val latestVersion: String? = null,
    val isUpdateAvailable: Boolean = false,
    val lastCheckedAt: String? = null,
)

