package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpExtractorOverrideDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.api.contract.system.YtDlpUpdateResponseDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode

/** `/system/…` through the real module over PostgreSQL, yt-dlp faked. */
class SystemRoutesTest :
    FunSpec({
        val app = routeTestApp()

        test("settings: defaults -> update (secrets are never echoed) -> read back") {
            val initial = app.client.get("/api/v1/system/settings") { asDevUser() }
            initial.status shouldBe HttpStatusCode.OK
            initial.body<SystemSettingsDto>() shouldBe SystemSettingsDto()

            val update =
                SystemSettingsDto(
                    ytDlp =
                    YtDlpSettingsDto(
                        cookiesFromBrowser = "firefox",
                        cookiesContent = "# Netscape HTTP Cookie File",
                        legacyServerConnect = true,
                        preferredAudioLanguages = listOf(" EN_us ", "de", "de", ""),
                        maxAdditionalAudioTracks = 20,
                        originalAudioLanguage = "RU",
                        subLangs = "ru,en_GB",
                        extractorOverrides = mapOf("rutube" to YtDlpExtractorOverrideDto(proxyEnabled = false)),
                    ),
                    proxy = ProxySettingsDto(
                        enabled = true,
                        type = "socks5",
                        host = "10.0.0.1",
                        port = 1080,
                        username = "u",
                        password = "p",
                    ),
                )
            val put =
                app.client.put("/api/v1/system/settings") {
                    asDevUser()
                    jsonBody(update)
                }
            put.status shouldBe HttpStatusCode.OK
            put.body<SystemSettingsDto>() shouldBe update.copy(proxy = update.proxy.copy(password = null))

            val stored = app.client.get("/api/v1/system/settings") { asDevUser() }.body<SystemSettingsDto>()
            stored.ytDlp shouldBe
                YtDlpSettingsDto(
                    cookiesFromBrowser = "firefox",
                    cookiesContent = null,
                    legacyServerConnect = true,
                    preferredAudioLanguages = listOf("en-us", "de"),
                    maxAdditionalAudioTracks = 8,
                    originalAudioLanguage = "ru",
                    preferredSubtitleLanguages = listOf("ru", "en-gb"),
                    subLangs = null,
                    extractorOverrides = mapOf("rutube" to YtDlpExtractorOverrideDto(proxyEnabled = false)),
                )
            stored.proxy shouldBe
                ProxySettingsDto(enabled = true, type = "SOCKS5", host = "10.0.0.1", port = 1080, username = "u")
        }

        test("yt-dlp status and update") {
            val status = app.client.get("/api/v1/system/yt-dlp/status") { asDevUser() }
            status.status shouldBe HttpStatusCode.OK
            val dto = status.body<YtDlpStatusDto>()
            dto.currentVersion shouldBe "2025.01.15"
            dto.latestVersion shouldBe "2025.02.10"
            dto.isUpdateAvailable shouldBe true
            dto.lastCheckedAt.shouldNotBeNull()

            val update = app.client.post("/api/v1/system/yt-dlp/update") { asDevUser() }
            update.status shouldBe HttpStatusCode.Accepted
            update.body<YtDlpUpdateResponseDto>() shouldBe
                YtDlpUpdateResponseDto("UPDATED", "Updated to version 2025.02.10")

            val after = app.client.get("/api/v1/system/yt-dlp/status") { asDevUser() }.body<YtDlpStatusDto>()
            after.isUpdateAvailable shouldBe false
            after.latestVersion shouldBe "2025.02.10"
            after.currentVersion shouldBe "2025.02.10"
            after.lastCheckedAt.shouldNotBeNull()
        }
    })
