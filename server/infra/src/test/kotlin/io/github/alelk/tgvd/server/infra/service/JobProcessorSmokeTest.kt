package io.github.alelk.tgvd.server.infra.service

import arrow.core.Either
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.video.DownloadEvent
import io.github.alelk.tgvd.domain.video.DownloadProgress
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.aJob
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.db.repository.ChannelRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobOutputRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.RuleRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.VideoInfoCacheImpl
import io.github.alelk.tgvd.server.infra.db.repository.WorkspaceRepositoryImpl
import io.github.alelk.tgvd.server.infra.process.FfmpegRunner
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Smoke test of [JobProcessor] on PostgreSQL with a fake [VideoDownloader]: a pending job goes through
 * the poller, the download and the output bookkeeping to `COMPLETED`. Every repository call of the
 * processor runs in its own transaction — a call outside one would fail here, not in production.
 */
class JobProcessorSmokeTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val clock = Clock.System
        val workspaces = WorkspaceRepositoryImpl()
        val jobs = JobRepositoryImpl(clock)
        val jobOutputs = JobOutputRepositoryImpl()
        val mediaRoot = Files.createTempDirectory("tgvd-job-processor").toFile()

        afterSpec { mediaRoot.deleteRecursively() }

        test("happy path: PENDING -> DOWNLOADING (progress) -> COMPLETED with the output recorded") {
            val downloader = FakeVideoDownloader()
            val processor =
                JobProcessor(
                    jobRepository = jobs,
                    jobOutputRepository = jobOutputs,
                    ruleRepository = RuleRepositoryImpl(clock),
                    channelRepository = ChannelRepositoryImpl(clock),
                    videoDownloader = downloader,
                    videoInfoCache = VideoInfoCacheImpl(clock),
                    ffmpegRunner = FfmpegRunner(FfmpegConfig()),
                    config = JobsConfig(maxConcurrentDownloads = 1, pollIntervalMs = 300),
                    txRunner = tx,
                    clock = clock,
                )
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            val target = File(mediaRoot, "music/Never Gonna Give You Up.mkv")
            val job =
                aJob(workspace.id, videoId = "processor-happy").copy(
                    storagePlan =
                    StoragePlan(
                        OutputTarget(FilePath(target.absolutePath), OutputFormat.OriginalVideo(MediaContainer.MKV)),
                    ),
                    mediaSelection = null,
                )
            tx.inRwTransaction { jobs.save(job) }.shouldBeRight()
            // `emit` returns after the processor has handled the event: the progress is committed by then.
            val committedProgress = CopyOnWriteArrayList<Pair<JobStatus, Int?>>()
            downloader.afterFirstProgress = {
                val current = tx.inRoTransaction { jobs.findById(job.id) }.shouldNotBeNull()
                committedProgress += current.status to current.progress
            }

            processor.start()
            try {
                val completed =
                    eventually(20.seconds) {
                        tx.inRoTransaction { jobs.findById(job.id) }.shouldNotBeNull().also {
                            it.status shouldBe JobStatus.COMPLETED
                        }
                    }

                completed.startedAt.shouldNotBeNull()
                completed.finishedAt.shouldNotBeNull()
                downloader.calls shouldHaveSize 1
                target.readText() shouldBe "video bytes"
                tx.inRoTransaction { jobOutputs.findByJob(job.id) }.map { it.path.value } shouldContain
                    target.absolutePath
                committedProgress shouldBe listOf(JobStatus.DOWNLOADING to 40)
            } finally {
                processor.stop()
            }
        }
    })

/** Writes the file yt-dlp would write and reports progress, then completion. */
private class FakeVideoDownloader : VideoDownloader {
    val calls = CopyOnWriteArrayList<FilePath>()
    var afterFirstProgress: suspend () -> Unit = {}

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
        calls += outputPath
        emit(DownloadEvent.Progress(DownloadProgress(40, 400, 1_000, null, null)))
        afterFirstProgress()
        File(outputPath.value).writeText("video bytes")
        emit(DownloadEvent.Progress(DownloadProgress(100, 1_000, 1_000, null, null)))
        emit(DownloadEvent.Completed(actualFormat = null))
    }
}
