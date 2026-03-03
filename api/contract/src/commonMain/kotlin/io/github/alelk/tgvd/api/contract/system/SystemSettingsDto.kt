package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class SystemSettingsDto(
    val ytDlp: YtDlpSettingsDto = YtDlpSettingsDto(),
    val proxy: ProxySettingsDto = ProxySettingsDto(),
)

@Serializable
data class YtDlpSettingsDto(
    val cookiesFromBrowser: String? = null,
    val cookiesFile: String? = null,
)

@Serializable
data class ProxySettingsDto(
    val enabled: Boolean = false,
    val type: String = "HTTP",
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
)

