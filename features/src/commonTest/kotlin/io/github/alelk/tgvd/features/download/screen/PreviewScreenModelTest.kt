package io.github.alelk.tgvd.features.download.screen

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.api.contract.channel.ChannelListResponseDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.features.fakes.FakeTgVideoDownloaderClient
import io.github.alelk.tgvd.features.fakes.anHttpError
import io.github.alelk.tgvd.features.fixtures.VIDEO_URL
import io.github.alelk.tgvd.features.fixtures.aChannelDto
import io.github.alelk.tgvd.features.fixtures.aMusicVideoMetadata
import io.github.alelk.tgvd.features.fixtures.aPreviewResponse
import io.github.alelk.tgvd.features.fixtures.anOriginalTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewScreenModelTest :
    FunSpec({
        val dispatcher = StandardTestDispatcher()
        beforeTest { Dispatchers.setMain(dispatcher) }
        afterTest { Dispatchers.resetMain() }

        test("the initial state is the server's preview, drawn as screen models") {
            val preview = aPreviewResponse(
                history = listOf(
                    DownloadHistoryEntryDto("job-0", "COMPLETED", "2026-02-03T10:00:00Z", null, "mkv"),
                    DownloadHistoryEntryDto("job-1", "CANCELLED", null, VideoQualityDto.SD_480, "mp4"),
                ),
            )
            val state = PreviewScreenModel(FakeTgVideoDownloaderClient(), preview).state.value

            state.video.title shouldBe "Source title"
            state.video.maxQualityLabel shouldBe "1080p"
            state.audioOptions.map { it.formatId } shouldContainExactly listOf("140-en", "140-ru")
            state.audioOptions.first().label shouldBe "en · 128 kb/s"
            state.subtitleOptions shouldContainExactly listOf(SubtitleOption("en", automaticOnly = false))
            state.selectedAudioIds shouldContainExactly listOf("140-en")
            state.selectedSubtitleLanguages shouldContainExactly listOf("en")
            state.history.map { it.status } shouldContainExactly
                listOf(DownloadHistoryStatus.COMPLETED, DownloadHistoryStatus.OTHER)
            state.history.first().finishedDate shouldBe "2026-02-03"
            state.matchedRule shouldBe MatchedRule(id = "rule-1", name = "Music")
            state.editor.artist shouldBe "Artist"
            state.channelChecked shouldBe false
            state.canDownload shouldBe true
        }

        test("opening the screen checks the channel directory") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.channelsResult = ChannelListResponseDto(items = listOf(aChannelDto(id = "dir-7"))).right()
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.Opened)
                advanceUntilIdle()

                client.channelQueries shouldContainExactly listOf("channel-1" to "youtube")
                model.state.value.channelChecked shouldBe true
                model.state.value.directoryChannelId shouldBe "dir-7"
            }
        }

        test("a failed channel check marks the channel as checked and not registered") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.channelsResult = anHttpError().left()
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.Opened)
                advanceUntilIdle()

                model.state.value.channelChecked shouldBe true
                model.state.value.directoryChannelId.shouldBeNull()
                model.state.value.failure.shouldBeNull()
            }
        }

        test("typing re-previews once after the debounce and keeps the person's edits") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.previewResult = {
                    aPreviewResponse(
                        metadata = aMusicVideoMetadata(artist = "Server artist", title = "Server title"),
                        original = anOriginalTarget(path = "/music/New.webm"),
                    ).right()
                }
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.MetadataChanged(MetadataField.ARTIST, "Ed"))
                model.onEvent(PreviewEvent.MetadataChanged(MetadataField.ARTIST, "Edited"))
                advanceTimeBy(500)
                client.previewRequests.shouldBeEmpty()

                advanceUntilIdle()

                client.previewRequests shouldHaveSize 1
                client.previewRequests.single().url shouldBe VIDEO_URL
                client.previewRequests.single().overrides shouldBe UserOverridesDto.MusicVideo(artist = "Edited")
                val state = model.state.value
                state.loading shouldBe false
                state.editor.artist shouldBe "Edited"
                state.editor.title shouldBe "Server title"
                state.editor.originalPath shouldBe "/music/New.webm"
            }
        }

        test("choosing a category re-previews at once and keeps the choice") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.previewResult = { aPreviewResponse().right() }
                client.gate = CompletableDeferred()
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.CategorySelected(CategoryDto.OTHER))
                model.state.value.editor.metadataType shouldBe CategoryDto.OTHER
                runCurrent()

                client.previewRequests.single().overrides shouldBe UserOverridesDto.Other()
                model.state.value.loading shouldBe true
                model.state.value.canRefetch shouldBe false
                client.gate?.complete(Unit)
                advanceUntilIdle()
                model.state.value.loading shouldBe false
                // The category is the person's choice: the server's answer does not reset it.
                model.state.value.editor.metadataType shouldBe CategoryDto.OTHER
            }
        }

        test("a failed re-preview keeps the form, lowers the spinner and shows the error") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.previewResult = { anHttpError("Extractor down").left() }
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.MetadataChanged(MetadataField.TITLE, "My title"))
                advanceUntilIdle()

                val state = model.state.value
                state.loading shouldBe false
                state.editor.title shouldBe "My title"
                state.failure shouldBe PreviewFailure(PreviewAction.RE_PREVIEW, anHttpError("Extractor down"))
            }
        }

        test("editing tags does not re-preview") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.MetadataChanged(MetadataField.TAGS, "a, b"))
                advanceUntilIdle()

                client.previewRequests.shouldBeEmpty()
                model.state.value.editor.tags shouldBe "a, b"
            }
        }

        test("refetch forces a fresh preview and drops the person's edits") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.previewResult = { request ->
                    if (request.force) {
                        aPreviewResponse(metadata = aMusicVideoMetadata(artist = "Fresh")).right()
                    } else {
                        anHttpError().left()
                    }
                }
                val model = PreviewScreenModel(client, aPreviewResponse())
                model.onEvent(PreviewEvent.MetadataChanged(MetadataField.TAGS, "mine"))
                model.onEvent(PreviewEvent.AudioTrackChecked("140-ru", checked = true))

                model.onEvent(PreviewEvent.RefetchClicked)
                model.state.value.loading shouldBe true
                advanceUntilIdle()

                client.previewRequests.single().force shouldBe true
                val state = model.state.value
                state.loading shouldBe false
                state.userEdits.shouldBeEmpty()
                state.editor.artist shouldBe "Fresh"
                state.editor.tags shouldBe "rock"
                state.selectedAudioIds shouldContainExactly listOf("140-en")
            }
        }

        test("download creates the job from the edited form and emits JobCreated") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                val model = PreviewScreenModel(client, aPreviewResponse())
                model.onEvent(PreviewEvent.MetadataChanged(MetadataField.TAGS, "live, 2026"))
                model.onEvent(PreviewEvent.AudioTrackChecked("140-ru", checked = true))
                model.onEvent(PreviewEvent.SubtitleLanguageChecked("en", checked = false))
                model.onEvent(PreviewEvent.OriginalContainerSelected(MediaContainerDto.MKV))
                model.onEvent(PreviewEvent.OriginalQualitySelected(null))

                client.gate = CompletableDeferred()
                model.onEvent(PreviewEvent.DownloadClicked)
                runCurrent()
                model.state.value.busy shouldBe true
                model.state.value.canDownload shouldBe false
                client.gate?.complete(Unit)
                advanceUntilIdle()

                model.effects.first() shouldBe PreviewEffect.JobCreated
                model.state.value.busy shouldBe false
                val request = client.createdJobs.single()
                request.ruleId shouldBe "rule-1"
                request.category shouldBe CategoryDto.MUSIC_VIDEO
                request.metadata shouldBe ResolvedMetadataDto.MusicVideo(
                    artist = "Artist",
                    title = "Title",
                    album = "Album",
                    tags = listOf("live", "2026"),
                )
                request.storagePlan.original.format shouldBe OutputFormatDto.OriginalVideo(MediaContainerDto.MKV)
                request.storagePlan.original.maxQuality.shouldBeNull()
                request.mediaSelection shouldBe
                    MediaSelectionDto(audioFormatIds = listOf("140-en", "140-ru"), subtitleLanguages = emptyList())
            }
        }

        test("a failed download lowers busy and shows the error") {
            runTest(dispatcher) {
                val client = FakeTgVideoDownloaderClient()
                client.createJobResult = { anHttpError("Quota exceeded").left() }
                val model = PreviewScreenModel(client, aPreviewResponse())

                model.onEvent(PreviewEvent.DownloadClicked)
                advanceUntilIdle()

                model.state.value.busy shouldBe false
                model.state.value.failure shouldBe
                    PreviewFailure(PreviewAction.CREATE_JOB, anHttpError("Quota exceeded"))
            }
        }

        test("download needs at least one audio track when the video offers a choice") {
            val model = PreviewScreenModel(FakeTgVideoDownloaderClient(), aPreviewResponse())

            model.onEvent(PreviewEvent.AudioTrackChecked("140-en", checked = false))

            model.state.value.selectedAudioIds.shouldBeEmpty()
            model.state.value.canDownload shouldBe false
        }
    })
