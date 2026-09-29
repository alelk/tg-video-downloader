package io.github.alelk.tgvd.server.job

import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.server.fakes.ControlledVideoDownloader
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.alelk.tgvd.server.infra.db.DatabaseFactory
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.OpenDatabase
import io.github.alelk.tgvd.server.infra.db.repository.ChannelRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobOutputRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.RuleRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.VideoInfoCacheImpl
import io.github.alelk.tgvd.server.infra.process.FfmpegRunner
import io.github.alelk.tgvd.server.infra.service.JobProcessor
import io.github.alelk.tgvd.server.infra.service.ShutdownCancellation
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.github.alelk.tgvd.server.route.WORKSPACES
import io.github.alelk.tgvd.server.route.asDevUser
import io.github.alelk.tgvd.server.route.createWorkspace
import io.github.alelk.tgvd.server.route.jsonBody
import io.github.alelk.tgvd.server.route.routeTestApp
import io.github.alelk.tgvd.server.route.toCreateJobRequest
import io.github.alelk.tgvd.server.route.videoUrl
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * **The mine of Step 01** — a cancelled job coming back to life, a job lost by a restart. The real
 * server module (routes, use-cases) and a real [JobProcessor] over the same PostgreSQL database; the
 * download is a [ControlledVideoDownloader] the test drives step by step.
 *
 * 1. cancel through the route mid-download → `CANCELLED` for good: the download is cancelled and
 *    `updated_at` never changes again (the CAS path, and the poller path for a silent download);
 * 2. `stop()` mid-download → `PENDING` with the same attempt; a new processor completes it;
 * 3. a row left in `downloading` before the start → requeued and completed.
 *
 * To see it fail (done for Stage 01.9): in `JobRepositoryImpl.transition` drop
 * `and (JobsTable.status inList …)` from the `WHERE` — an unconditional `UPDATE`. Test 1 (CAS path)
 * turns red: the late progress overwrites `CANCELLED` with `DOWNLOADING`, the download is never
 * cancelled. Dropping the requeue in `JobProcessor.stop` turns test 2 red; dropping the start-up
 * `recoverInterrupted()` turns test 3 red.
 */
@OptIn(ExperimentalUuidApi::class)
class JobLifecycleTest :
    FunSpec({
        val app = routeTestApp()
        val clock = Clock.System
        val mediaRoot = Files.createTempDirectory("tgvd-job-lifecycle").toFile()
        var openDatabase: OpenDatabase? = null
        val database by lazy { DatabaseFactory(app.dbConfig).open().also { openDatabase = it }.database }
        val tx by lazy { ExposedTransactionRunner(database) }
        val jobs = JobRepositoryImpl(clock)

        afterSpec {
            openDatabase?.close()
            mediaRoot.deleteRecursively()
        }

        fun processor(downloader: VideoDownloader, pollIntervalMs: Long) = JobProcessor(
            jobRepository = jobs,
            jobOutputRepository = JobOutputRepositoryImpl(),
            ruleRepository = RuleRepositoryImpl(clock),
            channelRepository = ChannelRepositoryImpl(clock),
            videoDownloader = downloader,
            videoInfoCache = VideoInfoCacheImpl(clock),
            ffmpegRunner = FfmpegRunner(FfmpegConfig()),
            config = JobsConfig(maxConcurrentDownloads = 1, pollIntervalMs = pollIntervalMs),
            txRunner = tx,
            clock = clock,
        )

        suspend fun job(id: JobId): Job = tx.inRoTransaction { jobs.findById(id) }.shouldNotBeNull()

        /** A pending job created through the API, with its file under [mediaRoot]. */
        suspend fun createJob(slug: String, videoId: String): JobId {
            app.client.createWorkspace(slug)
            app.knowsVideo(videoId)
            val preview =
                app.client.post("$WORKSPACES/$slug/preview") {
                    asDevUser()
                    jsonBody(PreviewRequestDto(url = videoUrl(videoId)))
                }.body<PreviewResponseDto>()
            val target = File(mediaRoot, "$slug/$videoId.mkv").absolutePath
            val created =
                app.client.post("$WORKSPACES/$slug/jobs") {
                    asDevUser()
                    jsonBody(
                        preview.toCreateJobRequest().copy(
                            storagePlan = StoragePlanDto(
                                OutputTargetDto(target, OutputFormatDto.OriginalVideo(MediaContainerDto.MKV)),
                            ),
                            mediaSelection = null,
                        ),
                    )
                }
            created.status shouldBe HttpStatusCode.Created
            return JobId(Uuid.parse(created.body<JobDto>().id))
        }

        suspend fun cancelThroughRoute(slug: String, id: JobId) {
            val response = app.client.post("$WORKSPACES/$slug/jobs/${id.value}/cancel") { asDevUser() }
            response.status shouldBe HttpStatusCode.OK
            response.body<JobDto>().status shouldBe "cancelled"
        }

        suspend fun awaitDownloading(id: JobId, progress: Int): Job = eventually(10.seconds) {
            job(id).also {
                it.status shouldBe JobStatus.DOWNLOADING
                it.progress shouldBe progress
            }
        }

        /** The job stays exactly [expected] (same `updated_at`, same everything) for a while. */
        suspend fun staysUnchanged(id: JobId, expected: Job) {
            repeat(5) {
                delay(200)
                job(id) shouldBe expected
            }
        }

        test("1. cancel through the route mid-download: the late progress write loses its CAS, CANCELLED stays") {
            val id = createJob("life-cancel", "life-1")
            val downloader = ControlledVideoDownloader()
            // One tick only (the first, which claims): the poller cannot see the cancel — only the CAS can.
            val processor = processor(downloader, pollIntervalMs = 60_000)
            processor.start()
            try {
                awaitDownloading(id, progress = 10)

                cancelThroughRoute("life-cancel", id)
                val cancelled = job(id)
                cancelled.status shouldBe JobStatus.CANCELLED

                // yt-dlp prints the next progress line after the cancel was committed.
                downloader.progress(50)

                val cause =
                    withClue("the download must be cancelled once its progress write finds the job CANCELLED") {
                        withTimeoutOrNull(10.seconds) { downloader.cancelled.await() }.shouldNotBeNull()
                    }
                cause.shouldNotBeInstanceOf<ShutdownCancellation>()
                job(id) shouldBe cancelled
                downloader.progress(60) // nobody listens any more
                staysUnchanged(id, cancelled)
            } finally {
                processor.stop()
            }
            // The shutdown requeues only jobs that are still processing: a cancelled one stays cancelled.
            job(id).status shouldBe JobStatus.CANCELLED
            downloader.calls shouldBe 1
        }

        test("1b. cancel through the route while the download is silent: the poller stops it") {
            val id = createJob("life-cancel-silent", "life-1b")
            val downloader = ControlledVideoDownloader()
            val processor = processor(downloader, pollIntervalMs = 200)
            processor.start()
            try {
                awaitDownloading(id, progress = 10)

                cancelThroughRoute("life-cancel-silent", id)
                val cancelled = job(id)

                withClue("the poller must cancel the download of a job that is no longer processing") {
                    withTimeoutOrNull(10.seconds) { downloader.cancelled.await() }.shouldNotBeNull()
                }
                staysUnchanged(id, cancelled)
                cancelled.status shouldBe JobStatus.CANCELLED
            } finally {
                processor.stop()
            }
            job(id).status shouldBe JobStatus.CANCELLED
        }

        test("2. stop() mid-download: back to PENDING with the same attempt; a new processor completes it") {
            val id = createJob("life-stop", "life-2")
            val first = ControlledVideoDownloader()
            val stopped = processor(first, pollIntervalMs = 200)
            stopped.start()
            awaitDownloading(id, progress = 10)

            stopped.stop()

            first.cancelled.isCompleted shouldBe true
            first.cancelled.await().shouldBeInstanceOf<ShutdownCancellation>()
            val requeued = job(id)
            requeued.status shouldBe JobStatus.PENDING
            requeued.attempt shouldBe 0
            requeued.progress.shouldBeNull()
            requeued.startedAt.shouldBeNull()

            val second = ControlledVideoDownloader().also { it.finish() }
            val next = processor(second, pollIntervalMs = 200)
            next.start()
            try {
                val completed =
                    eventually(20.seconds) { job(id).also { it.status shouldBe JobStatus.COMPLETED } }
                completed.attempt shouldBe 0
                second.calls shouldBe 1
                first.calls shouldBe 1
            } finally {
                next.stop()
            }
            job(id).status shouldBe JobStatus.COMPLETED
        }

        test("3. a job left in 'downloading' by a process that is gone is requeued at start and completed") {
            val id = createJob("life-restart", "life-3")
            PostgresTestContainer.connect(app.dbConfig).use { connection ->
                connection.prepareStatement(
                    "UPDATE jobs SET status = 'downloading', started_at = now(), " +
                        "progress = '{\"phase\":\"download\",\"percent\":37}'::jsonb WHERE id = ?",
                ).use {
                    it.setObject(1, java.util.UUID.fromString(id.value.toString()))
                    it.executeUpdate() shouldBe 1
                }
            }
            job(id).let { (it.status to it.progress) shouldBe (JobStatus.DOWNLOADING to 37) }

            val downloader = ControlledVideoDownloader().also { it.finish() }
            val processor = processor(downloader, pollIntervalMs = 200)
            processor.start()
            try {
                val completed =
                    eventually(20.seconds) { job(id).also { it.status shouldBe JobStatus.COMPLETED } }
                completed.attempt shouldBe 0
                downloader.calls shouldBe 1
            } finally {
                processor.stop()
            }
        }
    })
