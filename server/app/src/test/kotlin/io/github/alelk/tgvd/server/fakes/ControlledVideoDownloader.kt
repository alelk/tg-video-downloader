package io.github.alelk.tgvd.server.fakes

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.DownloadEvent
import io.github.alelk.tgvd.domain.video.DownloadProgress
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.video.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * A download the test drives step by step, like a yt-dlp process the test holds in its hand.
 *
 * Each download reports 10 %, then waits for the test: [progress] makes it report one more value,
 * [finish] makes it write the file and complete. While it waits it is suspended — cancelling the job's
 * coroutine cancels it, and [cancelled] records the cause (what would kill the yt-dlp process).
 */
class ControlledVideoDownloader : VideoDownloader {
    private val steps = Channel<Int>(Channel.UNLIMITED)

    /** Completes when a download has started (the job was claimed and handed to the downloader). */
    val started = CompletableDeferred<Unit>()

    /** Completes with the cancellation that stopped the download. */
    val cancelled = CompletableDeferred<CancellationException>()

    /** Completes when a download has written its file and reported completion. */
    val completed = CompletableDeferred<Unit>()

    private val callCount = AtomicInteger()

    val calls: Int get() = callCount.get()

    /** Report [percent] next. */
    fun progress(percent: Int) {
        steps.trySend(percent)
    }

    /** Write the file and complete. */
    fun finish() {
        steps.trySend(FINISH)
    }

    override suspend fun download(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo?,
        mediaSelection: MediaSelection?,
    ): Either<DomainError, FilePath> = error("JobProcessor uses downloadWithProgress")

    override fun downloadWithProgress(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo?,
        mediaSelection: MediaSelection?,
    ): Flow<DownloadEvent> = flow {
        callCount.incrementAndGet()
        started.complete(Unit)
        try {
            emit(progressEvent(10))
            while (true) {
                val step = steps.receive()
                if (step == FINISH) break
                emit(progressEvent(step))
            }
            File(outputPath.value).apply { parentFile?.mkdirs() }.writeText("video bytes")
            emit(progressEvent(100))
            emit(DownloadEvent.Completed(actualFormat = null))
            completed.complete(Unit)
        } catch (e: CancellationException) {
            cancelled.complete(e)
            throw e
        }
    }

    private fun progressEvent(percent: Int) =
        DownloadEvent.Progress(DownloadProgress(percent, percent * 10L, 1_000, null, null))

    private companion object {
        const val FINISH = -1
    }
}
