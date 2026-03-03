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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

            // 5. Resolve actual file (yt-dlp may add format suffixes like .f313.webm)
            val actualFile = resolveDownloadedFile(outputPath)
            if (actualFile == null) {
                jobRepository.updateStatus(
                    id = job.id,
                    status = JobStatus.FAILED,
                    errorMessage = "Downloaded file not found: ${outputPath.value} (also checked for yt-dlp format suffixes)",
                )
                return
            }
            val resolvedPath = if (actualFile.absolutePath != File(outputPath.value).absolutePath) {
                // Rename to expected path
                val target = File(outputPath.value)
                try {
                    Files.move(actualFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    logger.info { "Renamed '${actualFile.name}' → '${target.name}'" }
                    outputPath
                } catch (e: Exception) {
                    logger.warn { "Failed to rename '${actualFile.absolutePath}' → '${target.absolutePath}': ${e.message}, using actual path" }
                    FilePath(actualFile.absolutePath)
                }
            } else {
                outputPath
            }

            // 6. Process additional outputs (conversions/copies)
            if (job.storagePlan.additional.isNotEmpty()) {
                jobRepository.updateStatus(job.id, JobStatus.DOWNLOADING, JobPhase.CONVERT, 0)

                // Track completed outputs by conversion signature to reuse results
                val completedOutputs = mutableMapOf<ConversionKey, FilePath>()

                for ((index, target) in job.storagePlan.additional.withIndex()) {
                    val progress = ((index.toDouble() / job.storagePlan.additional.size) * 100).toInt()
                    jobRepository.updateStatus(job.id, JobStatus.DOWNLOADING, JobPhase.CONVERT, progress)

                    val key = ConversionKey.of(target)
                    val existingOutput = completedOutputs[key]
                    if (existingOutput != null) {
                        // Identical conversion already done — just copy
                        File(target.path.parent).mkdirs()
                        File(existingOutput.value).copyTo(File(target.path.value), overwrite = true)
                        logger.info { "Copied from identical output '${existingOutput.fileName}' → '${target.path.value}'" }
                    } else {
                        processAdditionalOutput(job, resolvedPath, target)
                        if (File(target.path.value).exists()) {
                            completedOutputs[key] = target.path
                        }
                    }
                }
            }

            // 7. Mark completed
            jobRepository.updateStatus(job.id, JobStatus.COMPLETED, progress = 100)

            logger.info { "Job ${job.id.value} completed: ${resolvedPath.value}" +
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
                val maxHeight = target.maxQuality?.toMaxHeight()
                ffmpegRunner.convertVideo(originalPath, target.path, format.container, maxHeight)
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
                        { error ->
                            logger.error { "Embed metadata failed: $error" }
                            File(tempPath.value).delete()
                        },
                        {
                            Files.move(
                                File(tempPath.value).toPath(),
                                File(convertedPath.value).toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
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
                        { error ->
                            logger.error { "Embed thumbnail failed: $error" }
                            File(tempPath.value).delete()
                        },
                        {
                            Files.move(
                                File(tempPath.value).toPath(),
                                File(convertedPath.value).toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
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
     * Resolve the actual downloaded file.
     * yt-dlp may produce files with format suffixes (e.g. "Title.f313.webm" instead of "Title.webm")
     * or different extensions when merging fails.
     */
    private fun resolveDownloadedFile(expectedPath: FilePath): File? {
        val expectedFile = File(expectedPath.value)
        if (expectedFile.exists()) return expectedFile

        val dir = expectedFile.parentFile ?: return null
        if (!dir.exists()) return null

        val expectedName = expectedFile.nameWithoutExtension  // e.g. "East To West (Live at The Ryman)"
        val expectedExt = expectedFile.extension               // e.g. "webm"

        // Look for files matching: "Title.f<N>.ext" or "Title.<something>.ext"
        val candidates = dir.listFiles()?.filter { f ->
            f.isFile && f.name != expectedFile.name &&
                f.name.startsWith(expectedName) &&
                f.name.endsWith(".$expectedExt")
        } ?: emptyList()

        if (candidates.size == 1) {
            logger.info { "Resolved yt-dlp output: '${candidates[0].name}' (expected: '${expectedFile.name}')" }
            return candidates[0]
        }

        // Also check for any file with the same base name but different extension
        val anyCandidates = dir.listFiles()?.filter { f ->
            f.isFile && f.nameWithoutExtension.startsWith(expectedName) &&
                f.extension in listOf("webm", "mkv", "mp4", "avi", "mov", "flv")
        } ?: emptyList()

        if (anyCandidates.size == 1) {
            logger.info { "Resolved yt-dlp output (different ext): '${anyCandidates[0].name}' (expected: '${expectedFile.name}')" }
            return anyCandidates[0]
        }

        if (anyCandidates.isNotEmpty()) {
            // Pick the largest file (most likely the merged result)
            val best = anyCandidates.maxByOrNull { it.length() }!!
            logger.warn { "Multiple candidates found, picking largest: '${best.name}' (${best.length()} bytes) from ${anyCandidates.map { it.name }}" }
            return best
        }

        logger.error { "Could not resolve downloaded file. Expected: '${expectedFile.name}', dir contents: ${dir.listFiles()?.map { it.name }}" }
        return null
    }

    /**
     * Find a thumbnail file next to the original download.
     * yt-dlp often saves thumbnails as .jpg/.webp/.png alongside the video.
     * May also add format suffixes (e.g. "Title.f313.jpg").
     */
    private fun findThumbnailFile(originalPath: FilePath): File? {
        val base = originalPath.value.substringBeforeLast('.')
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        // Try exact match first
        extensions.firstNotNullOfOrNull { ext ->
            File("$base.$ext").takeIf { it.exists() }
        }?.let { return it }

        // Try fuzzy match (yt-dlp may add format suffixes)
        val dir = File(originalPath.parent)
        if (!dir.exists()) return null
        val baseName = File(originalPath.value).nameWithoutExtension
        return dir.listFiles()?.firstOrNull { f ->
            f.isFile && f.nameWithoutExtension.startsWith(baseName) &&
                f.extension.lowercase() in extensions
        }
    }
}

/** Map VideoQuality to pixel height for ffmpeg scaling. */
private fun DownloadPolicy.VideoQuality.toMaxHeight(): Int? = when (this) {
    DownloadPolicy.VideoQuality.BEST -> null  // no scaling
    DownloadPolicy.VideoQuality.HD_1080 -> 1080
    DownloadPolicy.VideoQuality.HD_720 -> 720
    DownloadPolicy.VideoQuality.SD_480 -> 480
}

/**
 * Key that captures all parameters affecting the output file content.
 * Two outputs with the same [ConversionKey] produce identical files (only the path differs),
 * so the second one can be a simple file copy instead of a redundant ffmpeg conversion.
 */
private data class ConversionKey(
    val format: OutputFormat,
    val maxQuality: DownloadPolicy.VideoQuality?,
    val embedThumbnail: Boolean,
    val embedMetadata: Boolean,
    val embedSubtitles: Boolean,
    val normalizeAudio: Boolean,
) {
    companion object {
        fun of(target: OutputTarget) = ConversionKey(
            format = target.format,
            maxQuality = target.maxQuality,
            embedThumbnail = target.embedThumbnail,
            embedMetadata = target.embedMetadata,
            embedSubtitles = target.embedSubtitles,
            normalizeAudio = target.normalizeAudio,
        )
    }
}

