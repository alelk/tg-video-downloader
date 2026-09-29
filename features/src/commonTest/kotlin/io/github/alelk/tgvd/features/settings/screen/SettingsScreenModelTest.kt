package io.github.alelk.tgvd.features.settings.screen

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.features.fakes.FakeTgVideoDownloaderClient
import io.github.alelk.tgvd.features.fakes.anHttpError
import io.github.alelk.tgvd.features.settings.model.SettingsForm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsScreenModelTest :
    FunSpec({
        val dispatcher = StandardTestDispatcher()
        beforeTest { Dispatchers.setMain(dispatcher) }
        afterTest { Dispatchers.resetMain() }

        val serverSettings = SystemSettingsDto(
            ytDlp = YtDlpSettingsDto(
                cookiesFile = "/cookies.txt",
                writeSubs = false,
                writeAutoSubs = false,
                preferredSubtitleLanguages = emptyList(),
                sleepSubtitles = null,
                rateLimit = "5M",
            ),
            proxy = ProxySettingsDto(enabled = true, type = "SOCKS5", host = "proxy", port = 1080, password = "***"),
        )

        test("the screen starts loading and shows the defaults") {
            val state = SettingsScreenModel(FakeTgVideoDownloaderClient()).state.value

            state.loading shouldBe true
            state.form shouldBe SettingsForm()
            state.ytDlp.shouldBeNull()
        }

        test("opening the screen loads the yt-dlp status and the settings into the form") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.ytDlpStatusResult = YtDlpStatusDto("2026.01.01", "2026.02.01", isUpdateAvailable = true).right()
                client.settingsResult = serverSettings.right()
                val model = SettingsScreenModel(client)

                model.onEvent(SettingsEvent.Opened)
                advanceUntilIdle()

                val state = model.state.value
                state.loading shouldBe false
                state.failure.shouldBeNull()
                state.ytDlp shouldBe YtDlpStatus("2026.01.01", "2026.02.01", null, isUpdateAvailable = true)
                state.form shouldBe SettingsForm.from(serverSettings)
                state.form.proxyPassword shouldBe ""
                // Non-default advanced values expand their section; subtitles are all off and stay collapsed.
                state.advancedExpanded shouldBe true
                state.subtitlesExpanded shouldBe false
            }
        }

        test("a failed settings load keeps the status that did arrive, lowers the loader and shows the error") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.settingsResult = anHttpError("Database unavailable").left()
                val model = SettingsScreenModel(client)

                model.onEvent(SettingsEvent.Opened)
                advanceUntilIdle()

                val state = model.state.value
                state.loading shouldBe false
                state.ytDlp?.currentVersion shouldBe "2026.01.01"
                state.failure shouldBe SettingsFailure(SettingsAction.LOAD, anHttpError("Database unavailable"))
                state.form shouldBe SettingsForm()
            }
        }

        test("a reload keeps the status on screen instead of the loader, and never collapses an opened section") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                val model = SettingsScreenModel(client)
                model.onEvent(SettingsEvent.Opened)
                advanceUntilIdle()
                model.onEvent(SettingsEvent.SubtitlesToggled)

                model.onEvent(SettingsEvent.RetryClicked)
                model.state.value.loading shouldBe false
                advanceUntilIdle()

                client.settingsLoads shouldBe 2
                model.state.value.subtitlesExpanded shouldBe true
            }
        }

        test("save sends the edited form and emits Saved") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                val model = SettingsScreenModel(client)
                val edited = SettingsForm(proxyEnabled = true, proxyHost = "10.0.0.1", proxyPort = "3128")
                model.onEvent(SettingsEvent.FormChanged(edited))

                client.gate = CompletableDeferred()
                model.onEvent(SettingsEvent.SaveClicked)
                runCurrent()
                model.state.value.busy shouldBe true
                client.gate?.complete(Unit)
                advanceUntilIdle()

                model.effects.first() shouldBe SettingsEffect.Saved
                model.state.value.busy shouldBe false
                model.state.value.failure.shouldBeNull()
                client.savedSettings shouldContainExactly listOf(edited.toRequest())
            }
        }

        test("a failed save lowers busy and shows the error") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.updateSettingsResult = { anHttpError("Invalid proxy").left() }
                val model = SettingsScreenModel(client)

                model.onEvent(SettingsEvent.SaveClicked)
                advanceUntilIdle()

                model.state.value.busy shouldBe false
                model.state.value.failure shouldBe SettingsFailure(SettingsAction.SAVE, anHttpError("Invalid proxy"))
            }
        }

        test("updating yt-dlp reloads the status and the settings") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                val model = SettingsScreenModel(client)

                client.gate = CompletableDeferred()
                model.onEvent(SettingsEvent.UpdateYtDlpClicked)
                runCurrent()
                model.state.value.ytDlpUpdating shouldBe true
                client.gate?.complete(Unit)
                advanceUntilIdle()

                client.ytDlpUpdates shouldBe 1
                client.settingsLoads shouldBe 1
                model.state.value.ytDlpUpdating shouldBe false
            }
        }

        test("a failed yt-dlp update shows the error and does not reload") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.updateYtDlpResult = anHttpError("No network").left()
                val model = SettingsScreenModel(client)

                model.onEvent(SettingsEvent.UpdateYtDlpClicked)
                advanceUntilIdle()

                client.settingsLoads shouldBe 0
                model.state.value.ytDlpUpdating shouldBe false
                model.state.value.failure shouldBe
                    SettingsFailure(SettingsAction.UPDATE_YT_DLP, anHttpError("No network"))
            }
        }
    })
