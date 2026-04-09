package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class YtDlpSettingsDto(
    val cookiesFromBrowser: String? = null,
    val cookiesFile: String? = null,
)