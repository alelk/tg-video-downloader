package io.github.alelk.tgvd.api.mapping.system

import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpExtractorOverrideDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.domain.system.ProxySettings
import io.github.alelk.tgvd.domain.system.ProxyType
import io.github.alelk.tgvd.domain.system.SystemSettings
import io.github.alelk.tgvd.domain.system.YtDlpExtractorOverride
import io.github.alelk.tgvd.domain.system.YtDlpSettings
import io.github.alelk.tgvd.domain.system.YtDlpStatus
import io.github.alelk.tgvd.domain.system.YtDlpVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class SystemSettingsMappingTest :
    FunSpec({
        test("default settings map to the default DTO (the wire defaults are the server defaults)") {
            SystemSettings().toDto() shouldBe SystemSettingsDto()
        }

        test("toDto never sends the secrets") {
            val settings =
                SystemSettings(
                    ytDlp = YtDlpSettings(cookiesContent = "# cookies", cookiesFile = "/c.txt"),
                    proxy = ProxySettings(true, ProxyType.SOCKS5, "h", 1, "u", "secret"),
                )
            val dto = settings.toDto()
            dto.ytDlp.cookiesContent shouldBe null
            dto.ytDlp.cookiesFile shouldBe "/c.txt"
            dto.proxy shouldBe ProxySettingsDto(true, "SOCKS5", "h", 1, "u", null)
        }

        test("toUpdateRequest keeps every field; the proxy type is matched ignoring case, unknown is null") {
            val dto =
                SystemSettingsDto(
                    ytDlp =
                    YtDlpSettingsDto(
                        cookiesContent = "# c",
                        subLangs = "ru",
                        extractorOverrides = mapOf("vk" to YtDlpExtractorOverrideDto(proxyEnabled = true)),
                    ),
                    proxy = ProxySettingsDto(type = "socks5", password = "p"),
                )
            val request = dto.toUpdateRequest()
            request.ytDlp shouldBe
                YtDlpSettings(
                    cookiesContent = "# c",
                    subLangs = "ru",
                    extractorOverrides = mapOf("vk" to YtDlpExtractorOverride(proxyEnabled = true)),
                )
            request.proxy.type shouldBe ProxyType.SOCKS5
            request.proxy.password shouldBe "p"
            dto.copy(proxy = dto.proxy.copy(type = "ftp")).toUpdateRequest().proxy.type shouldBe null
        }

        test("the update response echoes the request without the proxy password") {
            val dto = SystemSettingsDto(proxy = ProxySettingsDto(type = "socks5", password = "p"))
            dto.toUpdateResponse() shouldBe SystemSettingsDto(proxy = ProxySettingsDto(type = "socks5"))
        }

        test("yt-dlp status and update response") {
            val checkedAt = Instant.parse("2026-01-01T09:00:00Z")
            YtDlpStatus(YtDlpVersion("1"), YtDlpVersion("2"), true, checkedAt).toDto() shouldBe
                YtDlpStatusDto("1", "2", true, "2026-01-01T09:00:00Z")
            YtDlpStatus(YtDlpVersion("1"), null, false, null).toDto() shouldBe YtDlpStatusDto("1")
            YtDlpVersion("2").toUpdateResponseDto().message shouldBe "Updated to version 2"
        }
    })
