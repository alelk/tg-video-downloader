package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.job.VideoDownloader
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import io.github.alelk.tgvd.domain.job.Job as DomainJob

private val logger = KotlinLogging.logger {}

/**
 * Background job processor that polls for pending jobs and executes downloads.
 *
 * Lifecycle: [start] launches a coroutine loop, [stop] cancels it gracefully.
 */
class JobProcessor(
    private val jobRepository: JobRepository,
    private val ruleRepository: RuleRepository,
    private val videoDownloader: VideoDownloader,
    private val config: JobsConfig,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("JobProcessor"))
    private val semaphore = Semaphore(config.maxConcurrentDownloads)

    fun start() {
        logger.info { "JobProcessor started (maxConcurrent=${config.maxConcurrentDownloads}, pollInterval=${config.pollIntervalMs}ms)" }
        scope.launch { pollLoop() }
    }

    fun stop() {
        logger.info { "JobProcessor stopping..." }
        scope.cancel()
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val pendingJobs = jobRepository.findActive()
                    .filter { it.status == JobStatus.PENDING }

                for (job in pendingJobs) {
                    semaphore.acquire()
                    scope.launch {
                        try {
                            processJob(job)
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Error in poll loop" }
            }

            delay(config.pollIntervalMs)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun processJob(job: DomainJob) {
        logger.info { "Processing job ${job.id.value}: ${job.source.url.value}" }

        try {
            // 1. Transition to DOWNLOADING
            jobRepository.updateStatus(job.id, JobStatus.DOWNLOADING, JobPhase.DOWNLOAD, 0)

            // 2. Resolve download policy from rule (if available)
            val rule = job.ruleId?.let { ruleRepository.findById(it) }
            val downloadPolicy = rule?.downloadPolicy ?: DownloadPolicy()

            // 3. Ensure output directory exists
            val outputPath = job.storagePlan.original.path
            File(outputPath.parent).mkdirs()

            // 4. Download with progress tracking
            videoDownloader.downloadWithProgress(job.source.url, outputPath, downloadPolicy)
                .collect { progress ->
                    jobRepository.updateStatus(
                        id = job.id,
                        status = JobStatus.DOWNLOADING,
                        phase = JobPhase.DOWNLOAD,
                        progress = progress.percent,
                    )
                }

            // 5. Verify file exists
            if (!File(outputPath.value).exists()) {
                jobRepository.updateStatus(
                    id = job.id,
                    status = JobStatus.FAILED,
                    errorMessage = "Downloaded file not found: ${outputPath.value}",
                )
                return
            }

            // 6. Mark completed
            jobRepository.updateStatus(job.id, JobStatus.COMPLETED, progress = 100)

            logger.info { "Job ${job.id.value} completed: ${outputPath.value}" }
        } catch (e: CancellationException) {
            jobRepository.updateStatus(job.id, JobStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Job ${job.id.value} failed" }
            jobRepository.updateStatus(
                id = job.id,
                status = JobStatus.FAILED,
                errorMessage = e.message ?: "Unknown error",
            )
        }
    }
}
