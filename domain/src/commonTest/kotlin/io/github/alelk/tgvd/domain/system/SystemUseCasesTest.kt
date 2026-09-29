package io.github.alelk.tgvd.domain.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** `/system/…` use-cases: settings read/update, yt-dlp status and update. */
class SystemUseCasesTest :
    FunSpec({
        context("UpdateSystemSettingsUseCase") {
            val requested =
                UpdateSystemSettingsRequest(
                    ytDlp =
                    YtDlpSettings(
                        cookiesFromBrowser = "firefox",
                        preferredAudioLanguages = listOf(" EN_us ", "de", "de", ""),
                        maxAdditionalAudioTracks = 20,
                        originalAudioLanguage = " RU ",
                        subLangs = "ru, en_GB,,ru",
                        extractorOverrides = mapOf("rutube" to YtDlpExtractorOverride(proxyEnabled = false)),
                    ),
                    proxy = ProxySettingsRequest(true, ProxyType.SOCKS5, "10.0.0.1", 1080, "u", "p"),
                )

            test("normalises languages, clamps the audio track count, turns legacy subLangs into the list") {
                val store = FakeSystemSettingsStore()
                val saved = UpdateSystemSettingsUseCase(store, NoopTransactionRunner())(requested)

                saved.ytDlp shouldBe
                    requested.ytDlp.copy(
                        preferredAudioLanguages = listOf("en-us", "de"),
                        maxAdditionalAudioTracks = 8,
                        originalAudioLanguage = "ru",
                        preferredSubtitleLanguages = listOf("ru", "en-gb"),
                        subLangs = null,
                    )
                saved.proxy shouldBe ProxySettings(true, ProxyType.SOCKS5, "10.0.0.1", 1080, "u", "p")
                store.current() shouldBe saved
            }

            test("without subLangs the subtitle list is normalised as given; a blank original language is dropped") {
                val store = FakeSystemSettingsStore()
                val saved =
                    UpdateSystemSettingsUseCase(store, NoopTransactionRunner())(
                        requested.copy(
                            ytDlp =
                            requested.ytDlp.copy(
                                subLangs = null,
                                preferredSubtitleLanguages = listOf("DE", " "),
                                originalAudioLanguage = "  ",
                                maxAdditionalAudioTracks = -1,
                            ),
                        ),
                    )
                saved.ytDlp.preferredSubtitleLanguages shouldBe listOf("de")
                saved.ytDlp.originalAudioLanguage shouldBe null
                saved.ytDlp.maxAdditionalAudioTracks shouldBe 0
            }

            test("missing secrets and an unknown proxy type keep the stored values") {
                val store =
                    FakeSystemSettingsStore(
                        SystemSettings(
                            ytDlp = YtDlpSettings(cookiesContent = "# cookies"),
                            proxy = ProxySettings(type = ProxyType.SOCKS5, password = "secret"),
                        ),
                    )
                val saved =
                    UpdateSystemSettingsUseCase(store, NoopTransactionRunner())(
                        UpdateSystemSettingsRequest(
                            ytDlp = YtDlpSettings(cookiesContent = null),
                            proxy = ProxySettingsRequest(false, null, "h", 1, null, null),
                        ),
                    )
                saved.ytDlp.cookiesContent shouldBe "# cookies"
                saved.proxy shouldBe ProxySettings(false, ProxyType.SOCKS5, "h", 1, null, "secret")
            }

            test("GetSystemSettingsUseCase returns the stored settings") {
                val settings = SystemSettings(proxy = ProxySettings(enabled = true))
                GetSystemSettingsUseCase(FakeSystemSettingsStore(settings))() shouldBe settings
            }
        }

        context("GetYtDlpStatusUseCase") {
            val clock = TestClock()

            test("an update is available when the latest release is newer; checkedAt is the clock's time") {
                val status =
                    GetYtDlpStatusUseCase(FakeYtDlpService("2025.01.15", "2025.02.10".right()), clock)()
                        .shouldBeRight()
                status shouldBe
                    YtDlpStatus(YtDlpVersion("2025.01.15"), YtDlpVersion("2025.02.10"), true, clock.now())
            }

            test("an unknown latest release is not an error: no update, no checkedAt") {
                val failure = DomainError.VideoExtractionFailed(Url("https://api.github.com"), "rate limited")
                GetYtDlpStatusUseCase(FakeYtDlpService("2025.01.15", failure.left()), clock)() shouldBe
                    YtDlpStatus(YtDlpVersion("2025.01.15"), null, false, null).right()
            }

            test("a missing yt-dlp binary is an error") {
                val failure = DomainError.VideoExtractionFailed(Url("https://yt-dlp.org"), "not found")
                val service = FakeYtDlpService("unused", "2025.02.10".right(), current = failure.left())
                GetYtDlpStatusUseCase(service, clock)() shouldBe failure.left()
            }
        }

        context("UpdateYtDlpUseCase") {
            test("updates when allowed") {
                val service = FakeYtDlpService("2025.01.15", "2025.02.10".right())
                UpdateYtDlpUseCase(service, FakeSystemSettingsStore(updateAllowed = true))() shouldBe
                    YtDlpVersion("2025.02.10").right()
                service.updates shouldBe 1
            }

            test("refuses with YtDlpUpdateDisabled when the administrator disabled updates") {
                val service = FakeYtDlpService("2025.01.15", "2025.02.10".right())
                UpdateYtDlpUseCase(service, FakeSystemSettingsStore(updateAllowed = false))() shouldBe
                    DomainError.YtDlpUpdateDisabled().left()
                service.updates shouldBe 0
            }
        }
    })

private class FakeSystemSettingsStore(
    private var settings: SystemSettings = SystemSettings(),
    private val updateAllowed: Boolean = true,
) : SystemSettingsStore {
    override fun current(): SystemSettings = settings

    override fun isYtDlpUpdateAllowed(): Boolean = updateAllowed

    override suspend fun save(settings: SystemSettings) {
        this.settings = settings
    }
}

private class FakeYtDlpService(
    installed: String,
    private val latest: Either<DomainError, String>,
    private val current: Either<DomainError, YtDlpVersion> = YtDlpVersion(installed).right(),
) : YtDlpService {
    var updates = 0
        private set

    override suspend fun version(): Either<DomainError, YtDlpVersion> = current

    override suspend fun latestVersion(): Either<DomainError, YtDlpVersion> = latest.map(::YtDlpVersion)

    override suspend fun update(): Either<DomainError, YtDlpVersion> {
        updates++
        return latestVersion()
    }
}
