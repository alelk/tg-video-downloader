package io.github.alelk.tgvd.domain.job

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
    ): Either<DomainError, FilePath>

    fun downloadWithProgress(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
    ): Flow<DownloadProgress>
}

data class DownloadProgress(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speed: String?,
    val eta: String?,
)
