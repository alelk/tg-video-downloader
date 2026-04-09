package io.github.alelk.tgvd.api.contract.system

import kotlinx.serialization.Serializable

@Serializable
data class SystemSettingsDto(
    val ytDlp: YtDlpSettingsDto = YtDlpSettingsDto(),
    val proxy: ProxySettingsDto = ProxySettingsDto(),
)

