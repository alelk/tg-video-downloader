package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.job.JobOutput
import io.github.alelk.tgvd.domain.job.JobOutputRepository
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.job.JobStatusPatch
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.VideoEncodeSettings
import io.github.alelk.tgvd.domain.storage.effectiveDownloadPolicy
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.DownloadEvent
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.alelk.tgvd.server.infra.process.FfmpegRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import io.github.alelk.tgvd.domain.job.Job as DomainJob

private val logger = KotlinLogging.logger {}

/**
 * Background job processor: claims pending jobs and runs their downloads.
 *
 * Lifecycle: [start] first puts back to `PENDING` every job a previous process left in a processing
 * status (`requeueInterrupted`, Fork 2 — the same attempt), then polls every `pollIntervalMs`; [stop]
 * stops claiming, cancels the running jobs and puts them back to `PENDING`. One server instance per
 * database: the start-up recovery would requeue jobs another live instance is working on.
 *
 * Each poll:
 * 1. the statuses of the running jobs are read; a job that is no longer `DOWNLOADING`/`POST_PROCESSING`
 *    (cancelled through the API, or moved by anyone else) has its coroutine cancelled — the yt-dlp/ffmpeg
 *    process tree dies with it;
 * 2. while a slot is free, the oldest pending job is claimed atomically (`claimNext`) and started.
 *
 * Every status write of a job is a compare-and-set from the processing statuses ([moveJob]). When it
 * finds the job moved by someone else, the job's coroutine is cancelled and writes nothing more — so a
 * cancelled job never comes back as `DOWNLOADING` or `COMPLETED`. `CANCELLED` is written only by
 * `CancelJobUseCase`; the processor never writes it, whatever the reason its coroutine ends.
 *
 * Transactions: the processor runs outside any use-case, so it opens them itself through [txRunner] —
 * every read a short read-only transaction, every write a short read-write one. yt-dlp, ffmpeg and file
 * work always run outside a transaction.
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("LongParameterList") // ports, runners and config of one background service; splitting it is Not in (Step 01)
class JobProcessor(
    private val jobRepository: JobRepository,
    private val jobOutputRepository: JobOutputRepository,
    private val ruleRepository: RuleRepository,
    private val channelRepository: ChannelRepository,
    private val videoDownloader: VideoDownloader,
    private val videoInfoCache: VideoInfoCache,
    private val ffmpegRunner: FfmpegRunner,
    private val config: JobsConfig,
    private val txRunner: TransactionRunner,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("JobProcessor"))

    /** Coroutines of the jobs this instance is processing; a slot is free while it is below the limit. */
    private val running = ConcurrentHashMap<JobId, Job>()

    @Volatile
    private var accepting = true

    @Volatile
    private var loop: Job? = null

    fun start() {
        logger.info {
            "JobProcessor started (maxConcurrent=${config.maxConcurrentDownloads}, " +
                "pollInterval=${config.pollIntervalMs}ms)"
        }
        loop =
            scope.launch {
                pollLoop()
            }.also { it.invokeOnCompletion { logger.info { "JobProcessor poll loop stopped" } } }
    }

    /**
     * Graceful stop: no more claims; the running jobs are cancelled with [ShutdownCancellation] (their
     * processes are terminated) and given up to [grace] to wind down; then — whatever happened, even if
     * this call is cancelled — each of them goes back to `PENDING` with the same attempt (a CAS from the
     * processing statuses, so a job that has just completed or been cancelled keeps its status).
     */
    suspend fun stop(grace: Duration = DEFAULT_STOP_GRACE) {
        logger.info { "JobProcessor stopping (${running.size} running job(s))..." }
        accepting = false
        val interrupted = mutableSetOf<JobId>()
        try {
            loop?.cancelAndJoin()
            interrupted += running.keys
            val jobs = running.values.toList()
            jobs.forEach { it.cancel(ShutdownCancellation()) }
            if (withTimeoutOrNull(grace) { jobs.joinAll() } == null) {
                logger.warn { "Running jobs did not stop within $grace; requeueing them anyway" }
            }
        } finally {
            withContext(NonCancellable) {
                interrupted += running.keys
                running.values.forEach { it.cancel(ShutdownCancellation()) }
                interrupted.forEach { requeue(it) }
            }
            scope.cancel()
            logger.info { "JobProcessor stopped; ${interrupted.size} job(s) returned to the queue" }
        }
    }

    @Suppress("TooGenericExceptionCaught") // a poller logs and continues on anything but cancellation
    private suspend fun pollLoop() {
        var recovered = false
        while (currentCoroutineContext().isActive) {
            try {
                // Strictly before the first claim: later it would requeue this instance's own jobs.
                if (!recovered) {
                    recoverInterrupted()
                    recovered = true
                }
                pollOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Job poll failed; retrying on the next tick" }
            }
            delay(config.pollIntervalMs)
        }
    }

    /** Start-up recovery (Fork 2): jobs a previous process left processing go back to the queue. */
    private suspend fun recoverInterrupted() {
        val requeued = txRunner.inRwTransaction { jobRepository.requeueInterrupted() }
        logger.info { "Returned $requeued interrupted job(s) to the queue" }
    }

    /**
     * One pass of the poller: first the running jobs that are no longer processing (cancelled through
     * the API, …) are stopped, then jobs are claimed while a slot is free.
     */
    private suspend fun pollOnce() {
        val ids = running.keys.toList()
        if (ids.isNotEmpty()) {
            val statuses = txRunner.inRoTransaction { ids.associateWith { jobRepository.findById(it)?.status } }
            val stale = statuses.filterValues { it == null || !it.isProcessing }
            stale.forEach { (id, status) ->
                logger.info { "Job ${id.value} is ${status ?: "gone"} now; stopping its processing" }
                running[id]?.cancel(JobNoLongerProcessing(id, "status is ${status ?: "gone"}"))
            }
            // The processes die before a new claim may start the same job again (after a retry).
            stale.keys.mapNotNull { running[it] }.joinAll()
        }
        while (accepting && running.size < config.maxConcurrentDownloads) {
            // Claim and registration are not interrupted halfway: a claimed job is always in [running]
            // before stop() looks, so stop() puts it back to the queue.
            val claimed =
                withContext(NonCancellable) {
                    txRunner.inRwTransaction { jobRepository.claimNext() }?.also { launchJob(it) }
                } ?: break
            logger.info { "Claimed job ${claimed.id.value}" }
        }
    }

    private fun launchJob(job: DomainJob) {
        val handle = scope.launch(CoroutineName("job-${job.id.value}"), start = CoroutineStart.LAZY) { processJob(job) }
        running[job.id] = handle
        handle.invokeOnCompletion { running.remove(job.id, handle) }
        handle.start()
    }

    @Suppress("TooGenericExceptionCaught") // shutdown must requeue every job it can, whatever fails for one
    private suspend fun requeue(id: JobId) {
        try {
            txRunner.inRwTransaction {
                jobRepository.transition(id, JobStatus.processingSourcesOf(JobStatus.PENDING), JobStatus.PENDING)
            }.onLeft { logger.info { "Job ${id.value} not requeued: ${it.message}" } }
        } catch (e: Exception) {
            logger.error(e) { "Failed to return job ${id.value} to the queue; the next start will" }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun processJob(job: DomainJob) {
        logger.info { "Processing job ${job.id.value}: ${job.source.url.value}" }

        try {
            // 1. The claim has already moved the job to DOWNLOADING (phase DOWNLOAD, 0 %).

            // 2. Resolve download policy: global settings < rule < channel track overrides
            val (rule, channel) = txRunner.inRoTransaction {
                val rule = job.ruleId?.let { ruleRepository.findById(it) }
                val channel = job.videoInfo?.let {
                    channelRepository.findByChannelId(job.workspaceId, it.channelId, it.extractor)
                }
                rule to channel
            }
            val basePolicy = effectiveDownloadPolicy(rule, channel)

            // Enable writeThumbnail if any output needs embedThumbnail
            val needsThumbnail = job.storagePlan.allTargets.any { it.embedThumbnail }
            val downloadPolicy = if (needsThumbnail) basePolicy.copy(writeThumbnail = true) else basePolicy

            // 3. Ensure output directory exists
            val outputPath = job.storagePlan.original.path
            File(outputPath.parent).mkdirs()

            // 4. Try to get VideoInfo from cache for better format selection
            val videoInfo = if (job.mediaSelection?.audioFormatIds != null) {
                job.videoInfo ?: cachedVideoInfo(job)
            } else {
                cachedVideoInfo(job) ?: job.videoInfo
            }
            if (videoInfo == null) {
                logger.warn {
                    "VideoInfo not found in cache for ${job.source.url.value}. " +
                        "Download might use generic format selection."
                }
            }

            // 4.5. Check if existing file has lower quality than requested — delete if so, skip if sufficient
            val existingFile = File(outputPath.value)
            if (existingFile.exists()) {
                val requestedMaxHeight = downloadPolicy.maxQuality.toMaxHeight()
                val existingHeight = ffmpegRunner.probeHeight(outputPath)
                val existingIsLowerQuality = when {
                    requestedMaxHeight == null -> false // BEST: treat existing file as sufficient
                    existingHeight == null -> true // can't probe → re-download to be safe
                    else -> existingHeight < requestedMaxHeight
                }
                if (existingIsLowerQuality) {
                    logger.info {
                        "Existing file has lower quality (${existingHeight}p < ${requestedMaxHeight}p) " +
                            "for job ${job.id.value}, deleting '${outputPath.value}' to re-download at higher quality"
                    }
                    existingFile.delete()
                } else {
                    val qualityDesc = if (existingHeight != null && requestedMaxHeight != null) {
                        "${existingHeight}p >= ${requestedMaxHeight}p"
                    } else {
                        "BEST policy"
                    }
                    logger.info {
                        "File already exists at sufficient quality ($qualityDesc) for job ${job.id.value}, " +
                            "skipping download"
                    }
                }
            }

            // Existing media may have different audio or be missing the chosen subtitles.
            // A retry also needs to recheck any sidecars from the failed attempt.
            if (!File(outputPath.value).exists() ||
                job.attempt > 0 ||
                job.mediaSelection?.audioFormatIds != null ||
                !job.mediaSelection?.subtitleLanguages.isNullOrEmpty()
            ) {
                videoDownloader.downloadWithProgress(
                    job.source.url,
                    outputPath,
                    downloadPolicy,
                    videoInfo,
                    job.mediaSelection,
                )
                    .collect { event ->
                        when (event) {
                            is DownloadEvent.Progress -> {
                                moveJob(
                                    job.id,
                                    JobStatus.DOWNLOADING,
                                    JobStatusPatch(phase = JobPhase.DOWNLOAD, progress = event.progress.percent),
                                )
                            }
                            is DownloadEvent.Completed -> {
                                event.actualFormat?.let { format ->
                                    logger.info {
                                        "Download completed for job ${job.id.value}. " +
                                            "Actual format: ${format.formatId} " +
                                            "(${format.width ?: "?"}x${format.height ?: "?"})"
                                    }
                                    val currentVideoInfo =
                                        txRunner.inRwTransaction {
                                            videoInfoCache.updateActualFormat(job.source.url.value, format)
                                            videoInfoCache.get(job.source.url.value)
                                        }
                                    // Update current job's videoInfo as well — a CAS like every job write
                                    currentVideoInfo?.let { info ->
                                        moveJob(
                                            job.id,
                                            JobStatus.DOWNLOADING,
                                            JobStatusPatch(phase = JobPhase.DOWNLOAD, progress = 100, videoInfo = info),
                                        )
                                    }
                                }
                            }
                        }
                    }
            }

            // 6. Resolve actual file (yt-dlp may add format suffixes like .f313.webm)
            val actualFile = resolveDownloadedFile(outputPath)
            if (actualFile == null) {
                moveJob(
                    job.id,
                    JobStatus.FAILED,
                    JobStatusPatch(
                        errorMessage =
                        "Downloaded file not found: ${outputPath.value} (also checked for yt-dlp format suffixes)",
                    ),
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
                    logger.warn {
                        "Failed to rename '${actualFile.absolutePath}' → '${target.absolutePath}': " +
                            "${e.message}, using actual path"
                    }
                    FilePath(actualFile.absolutePath)
                }
            } else {
                outputPath
            }

            // Track all produced output paths for job_outputs table
            val producedOutputs = mutableListOf<Pair<OutputTarget, FilePath>>()
            producedOutputs.add(job.storagePlan.original to resolvedPath)

            // 6. Process additional outputs (conversions/copies)
            if (job.storagePlan.additional.isNotEmpty()) {
                moveJob(job.id, JobStatus.DOWNLOADING, JobStatusPatch(phase = JobPhase.CONVERT, progress = 0))

                // Track completed outputs by conversion signature to reuse results
                val completedOutputs = mutableMapOf<ConversionKey, FilePath>()

                for ((index, target) in job.storagePlan.additional.withIndex()) {
                    val progress = ((index.toDouble() / job.storagePlan.additional.size) * 100).toInt()
                    moveJob(
                        job.id,
                        JobStatus.DOWNLOADING,
                        JobStatusPatch(phase = JobPhase.CONVERT, progress = progress),
                    )

                    val key = ConversionKey.of(target)
                    val existingOutput = completedOutputs[key]
                    if (existingOutput != null) {
                        // Identical conversion already done — just copy
                        File(target.path.parent).mkdirs()
                        File(existingOutput.value).copyTo(File(target.path.value), overwrite = true)
                        logger.info {
                            "Copied from identical output '${existingOutput.fileName}' → '${target.path.value}'"
                        }
                        producedOutputs.add(target to target.path)
                    } else {
                        processAdditionalOutput(job, resolvedPath, target)
                        if (File(target.path.value).exists()) {
                            completedOutputs[key] = target.path
                            producedOutputs.add(target to target.path)
                        }
                    }
                }
            }

            // 7. Persist job outputs to DB
            val now = clock.now()
            val outputRecords = producedOutputs.map { (target, path) ->
                JobOutput(
                    jobId = job.id,
                    format = target.format.serialized,
                    path = path,
                    sizeBytes = File(path.value).takeIf { it.exists() }?.length(),
                    createdAt = now,
                )
            }
            txRunner.inRwTransaction { jobOutputRepository.saveAll(outputRecords) }

            // 8. Mark completed (no phase: a finished job has no progress, as before)
            moveJob(job.id, JobStatus.COMPLETED)

            logger.info {
                val additional = job.storagePlan.additional.size
                "Job ${job.id.value} completed: ${resolvedPath.value}" +
                    if (additional > 0) " (+$additional additional outputs)" else ""
            }
        } catch (e: CancellationException) {
            // Cancelled (through the API, by a lost CAS, by a shutdown): whoever cancelled it owns the
            // status. Nothing is written here.
            logger.info { "Job ${job.id.value} stopped: ${e.message}" }
            throw e
        } catch (e: Exception) {
            // A failure caused by the cancellation (a killed process, a closed pipe) is not a job failure.
            currentCoroutineContext().ensureActive()
            logger.error(e) { "Job ${job.id.value} failed" }
            moveJob(job.id, JobStatus.FAILED, JobStatusPatch(errorMessage = e.message ?: "Unknown error"))
        }
    }

    /**
     * The only way the processor writes a job's status: one short read-write transaction with a
     * compare-and-set from the processing statuses that may reach [to]. A `Left` means the job was moved
     * by someone else (cancelled, requeued) or the write failed: the job's coroutine is cancelled and
     * writes nothing more.
     */
    private suspend fun moveJob(id: JobId, to: JobStatus, patch: JobStatusPatch = JobStatusPatch()) {
        txRunner
            .inRwTransaction { jobRepository.transition(id, JobStatus.processingSourcesOf(to), to, patch) }
            .onLeft { error ->
                val reason = JobNoLongerProcessing(id, error.message)
                currentCoroutineContext().cancel(reason)
                throw reason
            }
    }

    private suspend fun cachedVideoInfo(job: DomainJob): VideoInfo? =
        txRunner.inRoTransaction { videoInfoCache.get(job.source.url.value) }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun processAdditionalOutput(job: DomainJob, originalPath: FilePath, target: OutputTarget) {
        logger.info {
            "Processing additional output for job ${job.id.value}: ${target.path.value} (${target.format.serialized})"
        }

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
                val maxResolution = target.maxQuality?.toMaxResolution()
                ffmpegRunner.convertVideo(
                    originalPath,
                    target.path,
                    format.container,
                    maxResolution?.first,
                    maxResolution?.second,
                    target.encodeSettings,
                    target.embedSubtitles,
                )
                    .fold(
                        { error ->
                            throw RuntimeException(
                                "Conversion to ${format.container.extension} failed for ${target.path.value}: $error",
                            )
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
                            throw RuntimeException(
                                "Audio extraction (${format.format.extension}) failed for ${target.path.value}: $error",
                            )
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
                val tempPath = FilePath("$base.tmp_meta.$ext")
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
                val tempPath = FilePath("$base.tmp_thumb.$ext")
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

        val expectedName = expectedFile.nameWithoutExtension // e.g. "East To West (Live at The Ryman)"
        val expectedExt = expectedFile.extension // e.g. "webm"

        // Look for files matching: "Title.f<N>.ext" or "Title.<something>.ext"
        val candidates = dir.listFiles()?.filter { f ->
            f.isFile &&
                f.name != expectedFile.name &&
                f.name.startsWith(expectedName) &&
                f.name.endsWith(".$expectedExt")
        } ?: emptyList()

        if (candidates.size == 1) {
            logger.info { "Resolved yt-dlp output: '${candidates[0].name}' (expected: '${expectedFile.name}')" }
            return candidates[0]
        }

        // Also check for any file with the same base name but different extension
        val anyCandidates = dir.listFiles()?.filter { f ->
            f.isFile &&
                f.nameWithoutExtension.startsWith(expectedName) &&
                f.extension in listOf("webm", "mkv", "mp4", "avi", "mov", "flv")
        } ?: emptyList()

        if (anyCandidates.size == 1) {
            logger.info {
                "Resolved yt-dlp output (different ext): '${anyCandidates[0].name}' (expected: '${expectedFile.name}')"
            }
            return anyCandidates[0]
        }

        if (anyCandidates.isNotEmpty()) {
            // Pick the largest file (most likely the merged result)
            val best = anyCandidates.maxByOrNull { it.length() }!!
            logger.warn {
                "Multiple candidates found, picking largest: '${best.name}' (${best.length()} bytes) " +
                    "from ${anyCandidates.map { it.name }}"
            }
            return best
        }

        logger.error {
            "Could not resolve downloaded file. Expected: '${expectedFile.name}', dir contents: ${dir.listFiles()?.map {
                it.name
            }}"
        }
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
            f.isFile &&
                f.nameWithoutExtension.startsWith(baseName) &&
                f.extension.lowercase() in extensions
        }
    }
}

/** How long [JobProcessor.stop] waits for the cancelled jobs (and their processes) to wind down. */
val DEFAULT_STOP_GRACE: Duration = 10.seconds

/** The cause a shutdown cancels running jobs with; they are requeued, not cancelled for the user. */
class ShutdownCancellation : CancellationException("JobProcessor is stopping")

/** The job is not ours to process any more (its status was moved by someone else); nothing is written. */
@OptIn(ExperimentalUuidApi::class)
private class JobNoLongerProcessing(id: JobId, reason: String) :
    CancellationException("Job ${id.value} is no longer processed here: $reason")

/** Map VideoQuality to maximum resolution (width x height) for ffmpeg scaling. */
private fun DownloadPolicy.VideoQuality.toMaxResolution(): Pair<Int, Int>? = when (this) {
    DownloadPolicy.VideoQuality.BEST -> null // no scaling
    DownloadPolicy.VideoQuality.HD_1080 -> 1920 to 1080
    DownloadPolicy.VideoQuality.HD_720 -> 1280 to 720
    DownloadPolicy.VideoQuality.SD_480 -> 854 to 480
}

/** Map VideoQuality to maximum height in pixels for quality comparison. Null = no limit (BEST). */
private fun DownloadPolicy.VideoQuality.toMaxHeight(): Int? = when (this) {
    DownloadPolicy.VideoQuality.BEST -> null
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
    val encodeSettings: VideoEncodeSettings?,
    val embedThumbnail: Boolean,
    val embedMetadata: Boolean,
    val embedSubtitles: Boolean,
    val normalizeAudio: Boolean,
) {
    companion object {
        fun of(target: OutputTarget) = ConversionKey(
            format = target.format,
            maxQuality = target.maxQuality,
            encodeSettings = target.encodeSettings,
            embedThumbnail = target.embedThumbnail,
            embedMetadata = target.embedMetadata,
            embedSubtitles = target.embedSubtitles,
            normalizeAudio = target.normalizeAudio,
        )
    }
}
