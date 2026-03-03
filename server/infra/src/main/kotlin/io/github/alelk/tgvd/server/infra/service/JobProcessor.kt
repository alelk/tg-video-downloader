package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.job.VideoDownloader
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.alelk.tgvd.server.infra.process.FfmpegRunner
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
    private val ffmpegRunner: FfmpegRunner,
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
            val basePolicy = rule?.downloadPolicy ?: DownloadPolicy()

            // Enable writeThumbnail if any output needs embedThumbnail
            val needsThumbnail = job.storagePlan.allTargets.any { it.embedThumbnail }
            val downloadPolicy = if (needsThumbnail) basePolicy.copy(writeThumbnail = true) else basePolicy

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

            // 5. Verify original file exists
            if (!File(outputPath.value).exists()) {
                jobRepository.updateStatus(
                    id = job.id,
                    status = JobStatus.FAILED,
                    errorMessage = "Downloaded file not found: ${outputPath.value}",
                )
                return
            }

            // 6. Process additional outputs (conversions/copies)
            if (job.storagePlan.additional.isNotEmpty()) {
                jobRepository.updateStatus(job.id, JobStatus.DOWNLOADING, JobPhase.CONVERT, 0)

                for ((index, target) in job.storagePlan.additional.withIndex()) {
                    val progress = ((index.toDouble() / job.storagePlan.additional.size) * 100).toInt()
                    jobRepository.updateStatus(job.id, JobStatus.DOWNLOADING, JobPhase.CONVERT, progress)

                    processAdditionalOutput(job, outputPath, target)
                }
            }

            // 7. Mark completed
            jobRepository.updateStatus(job.id, JobStatus.COMPLETED, progress = 100)

            logger.info { "Job ${job.id.value} completed: ${outputPath.value}" +
                if (job.storagePlan.additional.isNotEmpty()) " (+${job.storagePlan.additional.size} additional outputs)" else ""
            }
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

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun processAdditionalOutput(
        job: DomainJob,
        originalPath: FilePath,
        target: OutputTarget,
    ) {
        logger.info { "Processing additional output for job ${job.id.value}: ${target.path.value} (${target.format.serialized})" }

        // Ensure target directory exists
        File(target.path.parent).mkdirs()

        // Step 1: Convert/copy to target format
        val convertedPath = when (val format = target.format) {
            is OutputFormat.OriginalVideo -> {
                // Same format type as original — just copy
                File(originalPath.value).copyTo(File(target.path.value), overwrite = true)
                logger.info { "Copied original to: ${target.path.value}" }
                target.path
            }
            is OutputFormat.ConvertedVideo -> {
                ffmpegRunner.convertVideo(originalPath, target.path, format.container)
                    .fold(
                        { error ->
                            logger.error { "Conversion failed for ${target.path.value}: $error" }
                            return
                        },
                        { path ->
                            logger.info { "Converted to ${format.container.extension}: ${target.path.value}" }
                            path
                        },
                    )
            }
            is OutputFormat.Audio -> {
                ffmpegRunner.extractAudio(originalPath, target.path, format.format)
                    .fold(
                        { error ->
                            logger.error { "Audio extraction failed for ${target.path.value}: $error" }
                            return
                        },
                        { path ->
                            logger.info { "Extracted audio as ${format.format.extension}: ${target.path.value}" }
                            path
                        },
                    )
            }
            is OutputFormat.Thumbnail -> {
                logger.warn { "Thumbnail extraction not yet implemented for: ${target.path.value}" }
                return
            }
        }

        // Step 2: Embed metadata tags (title, artist, etc.) if requested
        if (target.embedMetadata) {
            val metadataMap = buildMetadataMap(job.metadata)
            if (metadataMap.isNotEmpty()) {
                val ext = convertedPath.value.substringAfterLast('.', "")
                val base = convertedPath.value.substringBeforeLast('.')
                val tempPath = FilePath("${base}.tmp_meta.${ext}")
                ffmpegRunner.embedMetadata(convertedPath, tempPath, metadataMap)
                    .fold(
                        { error -> logger.error { "Embed metadata failed: $error" } },
                        {
                            File(tempPath.value).renameTo(File(convertedPath.value))
                            logger.info { "Embedded metadata into: ${convertedPath.value}" }
                        },
                    )
            }
        }

        // Step 3: Embed thumbnail if requested
        if (target.embedThumbnail) {
            // Look for a thumbnail file next to the original (yt-dlp may have downloaded one)
            val thumbnailFile = findThumbnailFile(originalPath)
            if (thumbnailFile != null) {
                val ext = convertedPath.value.substringAfterLast('.', "")
                val base = convertedPath.value.substringBeforeLast('.')
                val tempPath = FilePath("${base}.tmp_thumb.${ext}")
                ffmpegRunner.embedThumbnail(convertedPath, FilePath(thumbnailFile.absolutePath), tempPath)
                    .fold(
                        { error -> logger.error { "Embed thumbnail failed: $error" } },
                        {
                            File(tempPath.value).renameTo(File(convertedPath.value))
                            logger.info { "Embedded thumbnail into: ${convertedPath.value}" }
                        },
                    )
            } else {
                logger.warn { "No thumbnail file found for: ${originalPath.value}" }
            }
        }
    }

    /**
     * Build a map of metadata tags from resolved metadata.
     */
    private fun buildMetadataMap(metadata: ResolvedMetadata): Map<String, String> = buildMap {
        put("title", metadata.title)
        when (metadata) {
            is ResolvedMetadata.MusicVideo -> {
                put("artist", metadata.artist)
                metadata.album?.let { put("album", it) }
            }
            is ResolvedMetadata.SeriesEpisode -> {
                put("show", metadata.seriesName)
                metadata.season?.let { put("season_number", it) }
                metadata.episode?.let { put("episode_sort", it) }
            }
            is ResolvedMetadata.Other -> {}
        }
        metadata.year?.let { put("date", it.toString()) }
    }

    /**
     * Find a thumbnail file next to the original download.
     * yt-dlp often saves thumbnails as .jpg/.webp/.png alongside the video.
     */
    private fun findThumbnailFile(originalPath: FilePath): File? {
        val base = originalPath.value.substringBeforeLast('.')
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        return extensions.firstNotNullOfOrNull { ext ->
            File("$base.$ext").takeIf { it.exists() }
        }
    }
}
