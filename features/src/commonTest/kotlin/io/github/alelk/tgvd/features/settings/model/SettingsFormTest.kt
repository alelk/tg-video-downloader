package io.github.alelk.tgvd.features.settings.model

import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SettingsFormTest :
    FunSpec({
        test("the server's settings survive a round trip through the form, except the masked password") {
            val settings = SystemSettingsDto(
                ytDlp = YtDlpSettingsDto(
                    cookiesFromBrowser = "firefox",
                    legacyServerConnect = true,
                    preferredFormats = "bv*+ba",
                    preferredAudioLanguages = listOf("ru", "en"),
                    maxAdditionalAudioTracks = 3,
                    originalAudioLanguage = "ru",
                    sleepInterval = 2,
                    preferredSubtitleLanguages = listOf("ru", "en-us"),
                    sleepSubtitles = 5,
                    concurrentFragments = 8,
                    youtubePlayerClient = "web",
                    userAgent = "UA",
                ),
                proxy = ProxySettingsDto(enabled = true, type = "SOCKS5", host = "h", port = 1080, username = "u"),
            )

            val fromServer = settings.copy(proxy = settings.proxy.copy(password = "***"))
            SettingsForm.from(fromServer).toRequest() shouldBe settings
        }

        test("only the active cookies source is sent") {
            val form = SettingsForm(
                cookiesSource = CookiesSource.TEXT,
                cookiesFromBrowser = "chrome",
                cookiesContent = "# Netscape HTTP Cookie File",
                cookiesFile = "/cookies.txt",
            )

            val ytDlp = form.toRequest().ytDlp
            ytDlp.cookiesFromBrowser shouldBe null
            ytDlp.cookiesContent shouldBe "# Netscape HTTP Cookie File"
            ytDlp.cookiesFile shouldBe null
        }

        test("a cookies file is shown as the file source; otherwise the browser source is active") {
            SettingsForm.from(SystemSettingsDto(YtDlpSettingsDto(cookiesFile = "/c.txt"))).cookiesSource shouldBe
                CookiesSource.FILE
            SettingsForm.from(SystemSettingsDto(YtDlpSettingsDto(cookiesContent = "x"))).cookiesSource shouldBe
                CookiesSource.BROWSER
        }

        test("typed numbers fall back to their defaults and are clamped; language lists are normalised") {
            val request = SettingsForm(
                maxAdditionalAudioTracks = "42",
                concurrentFragments = "",
                socketTimeout = "x",
                proxyPort = "",
                subLangs = " RU , en_US, ru ",
                preferredAudioLanguages = "ru, , en, ru",
            ).toRequest()

            request.ytDlp.maxAdditionalAudioTracks shouldBe 8
            request.ytDlp.concurrentFragments shouldBe 5
            request.ytDlp.socketTimeout shouldBe 30
            request.proxy.port shouldBe 8080
            request.ytDlp.preferredSubtitleLanguages shouldBe listOf("ru", "en-us")
            request.ytDlp.preferredAudioLanguages shouldBe listOf("ru", "en")
        }

        test("sections expand only for non-default values") {
            SettingsForm().hasSubtitleSettings shouldBe false
            SettingsForm().hasAdvancedSettings shouldBe false
            SettingsForm(embedSubs = true).hasSubtitleSettings shouldBe true
            SettingsForm(socketTimeout = "60").hasAdvancedSettings shouldBe true
            SettingsForm(youtubePlayerClient = "").hasAdvancedSettings shouldBe true
        }
    })
