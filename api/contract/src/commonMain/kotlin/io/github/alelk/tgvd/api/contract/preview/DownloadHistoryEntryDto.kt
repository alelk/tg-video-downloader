package io.github.alelk.tgvd.api.contract.preview

import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import kotlinx.serialization.Serializable

/**
 * A single entry in the download history for a video URL.
 * Included in [PreviewResponseDto.previousDownloads].
 */
@Serializable
data class DownloadHistoryEntryDto(
    /** Job UUID */
    val jobId: String,
    /** Terminal job status: COMPLETED, FAILED, CANCELLED */
    val status: String,
    /** ISO-8601 instant when the job finished (null if not finished yet) */
    val finishedAt: String? = null,
    /** Configured max quality cap (null = best available) */
    val maxQuality: VideoQualityDto? = null,
    /** File extension of the primary output, e.g. "mkv", "mp4", "m4a" */
    val formatSummary: String,
)

