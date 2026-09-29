package io.github.alelk.tgvd.features.download.screen

import io.github.alelk.tgvd.api.client.ApiError
import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.features.common.Async
import io.github.alelk.tgvd.features.download.model.PreviewEditorValues
import io.github.alelk.tgvd.features.download.model.groupSubtitleOptions
import io.github.alelk.tgvd.features.download.model.maxAvailableQualityLabel
import io.github.alelk.tgvd.features.download.model.selectAudioOptions

/**
 * Everything the preview screen draws. The server's [PreviewResponseDto] is translated into the screen
 * models below by [PreviewUiState.withResponse]; the editable fields live in [editor].
 *
 * [loading] — a re-preview or refetch is in flight (the spinner in the top bar);
 * [busy] — the download job is being created.
 */
data class PreviewUiState(
    val video: PreviewVideo,
    val audioOptions: List<AudioTrackOption>,
    val subtitleOptions: List<SubtitleOption>,
    val history: List<DownloadHistoryItem>,
    val matchedRule: MatchedRule?,
    val metadataSource: String,
    val warnings: List<String>,
    val editor: PreviewEditorValues,
    val selectedAudioIds: List<String>,
    val selectedSubtitleLanguages: List<String>,
    /** The person changed the audio/subtitle selection: a server refresh no longer resets it. */
    val mediaSelectionEdited: Boolean = false,
    /** Editor fields the person changed: a server refresh keeps their values. */
    val userEdits: Set<String> = emptySet(),
    val channelChecked: Boolean = false,
    /** Id of the channel-directory entry for this video's channel; `null` — not in the directory (yet). */
    val directoryChannelId: String? = null,
    override val loading: Boolean = false,
    override val busy: Boolean = false,
    val failure: PreviewFailure? = null,
) : Async {
    override val error: ApiError? get() = failure?.error

    val fieldErrors: Map<String, String> get() = editor.fieldErrors

    val canRefetch: Boolean get() = !loading && !busy

    val canDownload: Boolean
        get() = !busy && !loading && fieldErrors.isEmpty() && (audioOptions.isEmpty() || selectedAudioIds.isNotEmpty())

    /**
     * Applies a server response: the read-only parts are replaced, the editor keeps the fields in [userEdits],
     * and the media selection is reset to the server default unless the person changed it.
     */
    fun withResponse(response: PreviewResponseDto): PreviewUiState {
        val defaults = response.defaultMediaSelection
        return copy(
            video = PreviewVideo.from(response),
            audioOptions = audioOptionsOf(response),
            subtitleOptions = groupSubtitleOptions(response.videoInfo.subtitleTracks).map { (language, tracks) ->
                SubtitleOption(language = language, automaticOnly = tracks.all { it.automatic })
            },
            history = response.previousDownloads.map(DownloadHistoryItem::from),
            matchedRule = response.matchedRule?.let { MatchedRule(id = it.id, name = it.name) },
            metadataSource = response.metadataSource.name,
            warnings = response.warnings,
            editor = editor.mergeServerResponse(response, userEdits),
            selectedAudioIds = if (mediaSelectionEdited) selectedAudioIds else defaults?.audioFormatIds.orEmpty(),
            selectedSubtitleLanguages =
            if (mediaSelectionEdited) selectedSubtitleLanguages else defaults?.subtitleLanguages.orEmpty(),
        )
    }

    companion object {
        fun initial(response: PreviewResponseDto): PreviewUiState = PreviewUiState(
            video = PreviewVideo.from(response),
            audioOptions = emptyList(),
            subtitleOptions = emptyList(),
            history = emptyList(),
            matchedRule = null,
            metadataSource = "",
            warnings = emptyList(),
            editor = PreviewEditorValues.from(response),
            selectedAudioIds = emptyList(),
            selectedSubtitleLanguages = emptyList(),
        ).withResponse(response)

        private fun audioOptionsOf(response: PreviewResponseDto): List<AudioTrackOption> = selectAudioOptions(
            formats = response.videoInfo.availableFormats,
            defaultAudioFormatIds = response.defaultMediaSelection?.audioFormatIds.orEmpty(),
        ).map { format ->
            AudioTrackOption(
                formatId = format.formatId,
                label = listOfNotNull(format.language, format.audioTrackName, format.tbr?.let { "${it.toInt()} kb/s" })
                    .distinct()
                    .joinToString(" · ")
                    .ifBlank { format.formatId },
            )
        }
    }
}

/** Read-only facts about the video. */
data class PreviewVideo(
    val title: String,
    val channelId: String,
    val channelName: String,
    val durationSeconds: Int,
    val extractor: String,
    val uploadDate: String?,
    /** "4K", "1080p", … — the best height among the available formats; `null` when unknown. */
    val maxQualityLabel: String?,
) {
    companion object {
        fun from(response: PreviewResponseDto): PreviewVideo = PreviewVideo(
            title = response.videoInfo.title,
            channelId = response.videoInfo.channelId,
            channelName = response.videoInfo.channelName,
            durationSeconds = response.videoInfo.durationSeconds,
            extractor = response.videoInfo.extractor,
            uploadDate = response.videoInfo.uploadDate,
            maxQualityLabel = maxAvailableQualityLabel(response.videoInfo.availableFormats),
        )
    }
}

data class AudioTrackOption(val formatId: String, val label: String)

/** One subtitle language; [automaticOnly] — only auto-generated tracks exist for it. */
data class SubtitleOption(val language: String, val automaticOnly: Boolean)

data class MatchedRule(val id: String, val name: String?)

/** A terminal job created earlier for the same URL. */
data class DownloadHistoryItem(
    val status: DownloadHistoryStatus,
    /** The date part of the finish instant (`yyyy-mm-dd`), `null` when not finished. */
    val finishedDate: String?,
    val maxQuality: VideoQualityDto?,
    val formatSummary: String,
) {
    companion object {
        fun from(entry: DownloadHistoryEntryDto): DownloadHistoryItem = DownloadHistoryItem(
            status = when (entry.status) {
                "COMPLETED" -> DownloadHistoryStatus.COMPLETED
                "FAILED" -> DownloadHistoryStatus.FAILED
                else -> DownloadHistoryStatus.OTHER
            },
            finishedDate = entry.finishedAt?.substringBefore("T"),
            maxQuality = entry.maxQuality,
            formatSummary = entry.formatSummary,
        )
    }
}

enum class DownloadHistoryStatus { COMPLETED, FAILED, OTHER }

/** A failed request and which action it belonged to (the text shown depends on the action). */
data class PreviewFailure(val action: PreviewAction, val error: ApiError)

enum class PreviewAction { RE_PREVIEW, REFETCH, CREATE_JOB }
