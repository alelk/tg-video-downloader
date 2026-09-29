package io.github.alelk.tgvd.domain.preview

import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoSource

/**
 * Everything the preview screen shows for one URL (see [PreviewVideoUseCase]).
 *
 * @param source the requested URL with the video's id and extractor.
 * @param result metadata, matched rule and output rules resolved by [PreviewUseCase].
 * @param storagePlan the output paths rendered from [PreviewResult.outputs].
 * @param appliedOverrides the user's overrides the preview was computed with.
 * @param previousDownloads finished (terminal) jobs of this video in the workspace, newest first.
 * @param defaultMediaSelection the tracks pre-selected for download.
 */
data class VideoPreview(
    val source: VideoSource,
    val result: PreviewResult,
    val storagePlan: StoragePlan,
    val appliedOverrides: UserOverrides?,
    val previousDownloads: List<Job>,
    val defaultMediaSelection: MediaSelection,
)
