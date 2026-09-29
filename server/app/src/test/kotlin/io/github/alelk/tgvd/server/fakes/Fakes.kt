package io.github.alelk.tgvd.server.fakes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.system.YtDlpVersion
import io.github.alelk.tgvd.domain.video.DownloadEvent
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.ConcurrentHashMap

/** yt-dlp extraction without a process: answers from [videos]; an unknown URL is `VideoExtractionFailed`. */
class FakeVideoInfoExtractor : VideoInfoExtractor {
    val videos = ConcurrentHashMap<String, VideoInfo>()
    val calls = ConcurrentHashMap<String, Int>()

    override suspend fun extract(url: String): Either<DomainError, VideoInfo> {
        calls.merge(url, 1, Int::plus)
        return videos[url]?.right() ?: DomainError.VideoExtractionFailed(Url(url), "unknown to the fake").left()
    }
}

/** A downloader that "downloads" instantly. Route tests never start the job processor, so it is not called there. */
class FakeVideoDownloader : VideoDownloader {
    override suspend fun download(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo?,
        mediaSelection: MediaSelection?,
    ): Either<DomainError, FilePath> = outputPath.right()

    override fun downloadWithProgress(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo?,
        mediaSelection: MediaSelection?,
    ): Flow<DownloadEvent> = flowOf(DownloadEvent.Completed(actualFormat = null))
}

/** yt-dlp version management without a binary or GitHub. */
class FakeYtDlpService(
    var current: YtDlpVersion = YtDlpVersion("2025.01.15"),
    var latest: YtDlpVersion = YtDlpVersion("2025.02.10"),
) : YtDlpService {
    override suspend fun version(): Either<DomainError, YtDlpVersion> = current.right()

    override suspend fun latestVersion(): Either<DomainError, YtDlpVersion> = latest.right()

    override suspend fun update(): Either<DomainError, YtDlpVersion> {
        current = latest
        return current.right()
    }
}
