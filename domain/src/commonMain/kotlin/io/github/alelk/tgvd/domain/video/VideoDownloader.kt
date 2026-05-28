package io.github.alelk.tgvd.domain.video

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import kotlinx.coroutines.flow.Flow

interface VideoDownloader {
    suspend fun download(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo? = null,
    ): Either<DomainError, FilePath>

    fun downloadWithProgress(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo? = null,
    ): Flow<DownloadEvent>
}

sealed class DownloadEvent {
    data class Progress(val progress: DownloadProgress) : DownloadEvent()
    data class Completed(val actualFormat: VideoInfo.Format?) : DownloadEvent()
}

data class DownloadProgress(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speed: String?,
    val eta: String?,
)
