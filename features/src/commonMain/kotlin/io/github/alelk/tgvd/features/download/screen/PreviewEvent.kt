package io.github.alelk.tgvd.features.download.screen

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto

/** What the person did on the preview screen (plus [Opened]: the screen became visible). */
sealed interface PreviewEvent {
    /** The screen entered composition (first time, or back from another screen/tab). */
    data object Opened : PreviewEvent

    data object RefetchClicked : PreviewEvent

    data class CategorySelected(val category: CategoryDto) : PreviewEvent

    data class MetadataChanged(val field: MetadataField, val value: String) : PreviewEvent

    data class AudioTrackChecked(val formatId: String, val checked: Boolean) : PreviewEvent

    data class SubtitleLanguageChecked(val language: String, val checked: Boolean) : PreviewEvent

    data class OriginalPathChanged(val path: String) : PreviewEvent

    data class OriginalContainerSelected(val container: MediaContainerDto) : PreviewEvent

    data class OriginalQualitySelected(val quality: VideoQualityDto?) : PreviewEvent

    data class AdditionalPathChanged(val index: Int, val path: String) : PreviewEvent

    data class AdditionalContainerSelected(val index: Int, val container: MediaContainerDto) : PreviewEvent

    data class AdditionalQualitySelected(val index: Int, val quality: VideoQualityDto?) : PreviewEvent

    data object DownloadClicked : PreviewEvent
}

/** Editable metadata fields; [key] is the name used in the user-edits set and in field errors. */
enum class MetadataField(val key: String, val refreshesPreview: Boolean = true) {
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    SERIES_NAME("seriesName"),
    SEASON("season"),
    EPISODE("episode"),
    TAGS("tags", refreshesPreview = false),
}

/** One-shot outcomes for the Entry. */
sealed interface PreviewEffect {
    /** The download job was created: leave the preview. */
    data object JobCreated : PreviewEffect
}
