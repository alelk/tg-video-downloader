package io.github.alelk.tgvd.features.download.screen

import cafe.adriel.voyager.core.model.screenModelScope
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.features.common.FeatureScreenModel
import io.github.alelk.tgvd.features.download.model.PreviewEditorValues
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder of the preview screen: the editor over a [PreviewResponseDto], debounced re-preview when
 * metadata changes, a forced refetch, the channel-directory check, and job creation.
 */
class PreviewScreenModel(private val client: TgVideoDownloaderClient, initialPreview: PreviewResponseDto) :
    FeatureScreenModel<PreviewUiState, PreviewEffect>(PreviewUiState.initial(initialPreview)) {
    /** The latest server response: source, video info and storage defaults for the job request. */
    private var preview: PreviewResponseDto = initialPreview

    /** The in-flight re-preview or refetch; a newer one replaces it. */
    private var refreshJob: Job? = null

    fun onEvent(event: PreviewEvent) {
        when (event) {
            PreviewEvent.Opened -> checkChannel()
            PreviewEvent.RefetchClicked -> refetch()
            is PreviewEvent.CategorySelected -> {
                mutableState.update {
                    it.copy(
                        editor = it.editor.copy(category = event.category, metadataType = event.category),
                        userEdits = it.userEdits + CATEGORY_EDIT,
                    )
                }
                rePreview(debounceMs = 0)
            }
            is PreviewEvent.MetadataChanged -> {
                mutableState.update {
                    it.copy(
                        editor = it.editor.with(event.field, event.value),
                        userEdits = it.userEdits + event.field.key,
                    )
                }
                if (event.field.refreshesPreview) rePreview(debounceMs = TYPING_DEBOUNCE_MS)
            }
            is PreviewEvent.AudioTrackChecked -> mutableState.update {
                it.copy(
                    selectedAudioIds = it.selectedAudioIds.withChecked(event.formatId, event.checked),
                    mediaSelectionEdited = true,
                )
            }
            is PreviewEvent.SubtitleLanguageChecked -> mutableState.update {
                it.copy(
                    selectedSubtitleLanguages = it.selectedSubtitleLanguages.withChecked(event.language, event.checked),
                    mediaSelectionEdited = true,
                )
            }
            is PreviewEvent.OriginalPathChanged,
            is PreviewEvent.OriginalContainerSelected,
            is PreviewEvent.OriginalQualitySelected,
            is PreviewEvent.AdditionalPathChanged,
            is PreviewEvent.AdditionalContainerSelected,
            is PreviewEvent.AdditionalQualitySelected,
            -> editStoragePlan(event)
            PreviewEvent.DownloadClicked -> createJob()
        }
    }

    /** Non-critical: on an error the channel is simply treated as not registered (the last answer stays). */
    private fun checkChannel() {
        screenModelScope.launch {
            try {
                client.getChannels(channelId = preview.videoInfo.channelId, extractor = preview.videoInfo.extractor)
                    .onRight { result ->
                        mutableState.update { it.copy(directoryChannelId = result.items.firstOrNull()?.id) }
                    }
            } finally {
                mutableState.update { it.copy(channelChecked = true) }
            }
        }
    }

    /**
     * Asks the server to re-resolve the preview with the person's edits (after [debounceMs] of quiet).
     * A failure keeps the form as it is and only shows a warning.
     */
    private fun rePreview(debounceMs: Long) {
        val previous = refreshJob
        refreshJob = screenModelScope.launch {
            previous?.cancelAndJoin()
            if (debounceMs > 0) delay(debounceMs)
            val overrides = state.value.editor.toOverrides(state.value.userEdits) ?: return@launch
            runRequest(
                call = { client.preview(PreviewRequestDto(url = preview.source.url, overrides = overrides)) },
                started = { it.copy(loading = true) },
                finished = { it.copy(loading = false) },
                onFailure = { s, error -> s.copy(failure = PreviewFailure(PreviewAction.RE_PREVIEW, error)) },
                onSuccess = { s, response -> s.withResponse(response).copy(failure = null) },
            ).onRight { preview = it }
        }
    }

    /** Bypasses the server cache and drops the person's edits. */
    private fun refetch() {
        val previous = refreshJob
        mutableState.update { it.copy(loading = true, failure = null) }
        refreshJob = screenModelScope.launch {
            previous?.cancelAndJoin()
            runRequest(
                call = { client.preview(PreviewRequestDto(url = preview.source.url, force = true)) },
                started = { it.copy(loading = true) },
                finished = { it.copy(loading = false) },
                onFailure = { s, error -> s.copy(failure = PreviewFailure(PreviewAction.REFETCH, error)) },
                onSuccess = { s, response ->
                    s.copy(userEdits = emptySet(), mediaSelectionEdited = false).withResponse(response)
                },
            ).onRight { preview = it }
        }
    }

    private fun createJob() {
        val current = state.value
        if (current.busy) return
        val request = CreateJobRequestDto(
            source = preview.source,
            ruleId = preview.matchedRule?.id,
            category = current.editor.category,
            videoInfo = preview.videoInfo,
            metadata = current.editor.toMetadata(),
            storagePlan = current.editor.toStoragePlan(preview.storagePlan.original),
            mediaSelection = MediaSelectionDto(
                audioFormatIds = current.selectedAudioIds.takeIf { current.audioOptions.isNotEmpty() },
                subtitleLanguages = current.selectedSubtitleLanguages,
            ),
        )
        screenModelScope.launch {
            runRequest(
                call = { client.createJob(request) },
                started = { it.copy(busy = true, failure = null) },
                finished = { it.copy(busy = false) },
                onFailure = { s, error -> s.copy(failure = PreviewFailure(PreviewAction.CREATE_JOB, error)) },
                onSuccess = { s, _ -> s },
            ).onRight { sendEffect(PreviewEffect.JobCreated) }
        }
    }

    /** Storage-plan edits: they change what is saved where, not the metadata, so no re-preview. */
    private fun editStoragePlan(event: PreviewEvent) {
        when (event) {
            is PreviewEvent.OriginalPathChanged -> editEditor { it.copy(originalPath = event.path) }
            is PreviewEvent.OriginalContainerSelected ->
                editEditor { it.copy(originalFormat = OutputFormatDto.OriginalVideo(event.container)) }
            is PreviewEvent.OriginalQualitySelected -> editEditor { it.copy(originalMaxQuality = event.quality) }
            is PreviewEvent.AdditionalPathChanged -> editAdditional(event.index) { it.copy(path = event.path) }
            is PreviewEvent.AdditionalContainerSelected ->
                editAdditional(event.index) { it.copy(format = OutputFormatDto.OriginalVideo(event.container)) }
            is PreviewEvent.AdditionalQualitySelected ->
                editAdditional(event.index) { it.copy(maxQuality = event.quality) }
            else -> Unit
        }
    }

    private fun editEditor(change: (PreviewEditorValues) -> PreviewEditorValues) {
        mutableState.update { it.copy(editor = change(it.editor)) }
    }

    private fun editAdditional(index: Int, change: (OutputTargetDto) -> OutputTargetDto) {
        editEditor { editor ->
            editor.copy(
                additionalOutputs = editor.additionalOutputs.mapIndexed { i, target ->
                    if (i == index) change(target) else target
                },
            )
        }
    }

    private companion object {
        const val CATEGORY_EDIT = "category"
        const val TYPING_DEBOUNCE_MS = 700L
    }
}

private fun PreviewEditorValues.with(field: MetadataField, value: String): PreviewEditorValues = when (field) {
    MetadataField.TITLE -> copy(title = value)
    MetadataField.ARTIST -> copy(artist = value)
    MetadataField.ALBUM -> copy(album = value)
    MetadataField.SERIES_NAME -> copy(seriesName = value)
    MetadataField.SEASON -> copy(season = value)
    MetadataField.EPISODE -> copy(episode = value)
    MetadataField.TAGS -> copy(tags = value)
}

/** Adds (keeping the order of selection) or removes [item]. */
private fun List<String>.withChecked(item: String, checked: Boolean): List<String> = when {
    checked && item !in this -> this + item
    !checked -> this - item
    else -> this
}
