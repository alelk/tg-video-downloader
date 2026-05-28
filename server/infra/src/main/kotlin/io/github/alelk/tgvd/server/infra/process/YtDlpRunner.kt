package io.github.alelk.tgvd.server.infra.process

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.video.DownloadEvent
import io.github.alelk.tgvd.domain.video.DownloadProgress
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpExtractorOverride
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

class YtDlpRunner(
    private val settingsHolder: SystemSettingsHolder,
) : VideoInfoExtractor, VideoDownloader {

    private val config: YtDlpConfig get() = settingsHolder.ytDlpConfig
    private val proxyConfig: ProxyConfig get() = settingsHolder.proxyConfig

    /**
     * Enrich ProcessBuilder PATH with common binary locations
     * (homebrew, deno, user-local) that JVM may not inherit.
     */
    private fun ProcessBuilder.enrichPath(): ProcessBuilder = apply {
        val env = environment()
        val currentPath = env["PATH"] ?: ""
        val extraPaths = listOf(
            "/opt/homebrew/bin",
            "/usr/local/bin",
            System.getProperty("user.home") + "/.deno/bin",
        ).filter { java.io.File(it).isDirectory }
        val missing = extraPaths.filter { it !in currentPath }
        if (missing.isNotEmpty()) {
            env["PATH"] = (missing + currentPath).joinToString(":")
        }
    }

    /** Append cookies arguments from config (--cookies-from-browser or --cookies). */
    private fun MutableList<String>.addCookiesArgs() {
        config.cookiesFromBrowser?.takeIf { it.isNotBlank() }?.let { add("--cookies-from-browser"); add(it) }
        config.cookiesFile?.takeIf { it.isNotBlank() }?.let { add("--cookies"); add(it) }
    }

    /**
     * Resolve the per-extractor override for the given URL.
     * Matches by checking whether the URL contains any key from [YtDlpConfig.extractorOverrides]
     * as a substring (case-insensitive). Returns the first match, or null if none.
     */
    private fun resolveOverride(url: String): YtDlpExtractorOverride? =
        config.extractorOverrides.entries
            .firstOrNull { (key, _) -> url.contains(key, ignoreCase = true) }
            ?.value

    /** Effective SSL flags for the given URL (global merged with per-extractor override). */
    private fun effectiveLegacyServerConnect(url: String): Boolean =
        resolveOverride(url)?.legacyServerConnect ?: config.legacyServerConnect

    private fun effectiveNoCheckCertificate(url: String): Boolean =
        resolveOverride(url)?.noCheckCertificate ?: config.noCheckCertificate

    /** Effective proxy URL for the given URL (respects per-extractor proxyEnabled override). */
    private fun effectiveProxyUrl(url: String): String? {
        val override = resolveOverride(url)
        return when (override?.proxyEnabled) {
            false -> null  // explicitly disabled for this extractor
            true  -> proxyConfig.copy(enabled = true).toUrl()
            null  -> proxyConfig.toUrl()  // inherit global setting
        }
    }

    /** Append SSL workaround arguments for the given URL. */
    private fun MutableList<String>.addSslArgs(url: String) {
        if (effectiveLegacyServerConnect(url)) add("--legacy-server-connect")
        if (effectiveNoCheckCertificate(url)) add("--no-check-certificate")
    }

    /**
     * Format selector for a given quality.
     *
     * Strategy:
     * 1. If [videoInfo] is provided, try to pre-select best video and audio format IDs
     *    manually from [VideoInfo.availableFormats] to ensure the highest quality is used.
     * 2. Otherwise, use `bestvideo*+bestaudio/bestvideo*` as format selector.
     * 3. IMPORTANT: We use `-S` (--format-sort) to ensure resolution is prioritized.
     */
    private fun MutableList<String>.addFormatArgs(
        quality: DownloadPolicy.VideoQuality,
        videoInfo: VideoInfo? = null,
    ) {
        val formats = videoInfo?.availableFormats
        if (formats != null && formats.isNotEmpty()) {
            val bestFormatId = resolveBestFormatId(formats, quality)
            if (bestFormatId != null) {
                add("-f"); add(bestFormatId)
                // We still add -S for safety, but with specific format ID it's less critical
                add("-S"); add(when (quality) {
                    DownloadPolicy.VideoQuality.BEST -> "res,tbr,fps"
                    DownloadPolicy.VideoQuality.HD_1080 -> "res:1080,tbr,fps"
                    DownloadPolicy.VideoQuality.HD_720 -> "res:720,tbr,fps"
                    DownloadPolicy.VideoQuality.SD_480 -> "res:480,tbr,fps"
                })
                return
            }
        }

        // Fallback to general strategy
        add("-f"); add("bestvideo*+bestaudio/bestvideo*")
        add("--check-formats")
        when (quality) {
            DownloadPolicy.VideoQuality.BEST -> {
                add("-S"); add("res,tbr,fps")
            }
            DownloadPolicy.VideoQuality.HD_1080 -> {
                add("-S"); add("res:1080,tbr,fps")
            }
            DownloadPolicy.VideoQuality.HD_720 -> {
                add("-S"); add("res:720,tbr,fps")
            }
            DownloadPolicy.VideoQuality.SD_480 -> {
                add("-S"); add("res:480,tbr,fps")
            }
        }
    }

    internal fun resolveBestFormatId(
        formats: List<VideoInfo.Format>,
        quality: DownloadPolicy.VideoQuality,
    ): String? {
        val maxRes = when (quality) {
            DownloadPolicy.VideoQuality.BEST -> Int.MAX_VALUE
            DownloadPolicy.VideoQuality.HD_1080 -> 1080
            DownloadPolicy.VideoQuality.HD_720 -> 720
            DownloadPolicy.VideoQuality.SD_480 -> 480
        }

        val videoFormats = formats.filter { it.vcodec != null && it.vcodec != "none" }
        val audioFormats = formats.filter { (it.acodec != null && it.acodec != "none") && (it.vcodec == null || it.vcodec == "none") }

        val bestVideo = videoFormats
            .filter { (it.height ?: 0) <= maxRes }
            .sortedWith(
                compareByDescending<VideoInfo.Format> { it.height ?: 0 }
                    .thenByDescending { it.width ?: 0 }
                    .thenByDescending { it.tbr ?: 0.0 }
                    .thenByDescending { it.fps ?: 0.0 }
            ).firstOrNull() ?: videoFormats.minByOrNull { it.height ?: 0 }

        val bestAudio = audioFormats.sortedWith(
            compareByDescending<VideoInfo.Format> { it.tbr ?: 0.0 }
        ).firstOrNull()

        return when {
            bestVideo != null && bestAudio != null -> "${bestVideo.formatId}+${bestAudio.formatId}"
            bestVideo != null -> bestVideo.formatId
            bestAudio != null -> bestAudio.formatId
            else -> null
        }
    }

    /** Append retry/resilience arguments for robust downloads on slow/unstable networks. */
    private fun MutableList<String>.addResilienceArgs() {
        // Retry individual fragment downloads more aggressively
        add("--extractor-retries"); add("5")
        // Sleep between fragment retries to avoid rate limiting and let transient issues resolve
        add("--retry-sleep"); add("fragment:exp=1:5:30")
        // Sleep between file-level retries
        add("--retry-sleep"); add("http:exp=1:2:30")
        // Socket timeout — longer than default (20s) to tolerate slow connections
        add("--socket-timeout"); add("30")
        // Concurrent fragment downloads — speeds up DASH/HLS downloads significantly
        add("--concurrent-fragments"); add("5")
    }

    override suspend fun extract(url: String): Either<DomainError, VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(config.path)
                add("--dump-json")
                add("--no-download")
                add("--no-playlist")
                addCookiesArgs()
                addSslArgs(url)
                effectiveProxyUrl(url)?.let { add("--proxy"); add(it) }
                add(url)
            }

            logger.info { "Extracting video info: yt-dlp --dump-json $url" }
            val process = ProcessBuilder(args)
                .redirectErrorStream(false)
                .enrichPath()
                .start()

            val stdoutDeferred = async { process.inputStream.bufferedReader().use { it.readText() } }
            val stderrDeferred = async { process.errorStream.bufferedReader().use { it.readText() } }
            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "yt-dlp extract failed (exit=$exitCode): $stderr" }
                return@withContext DomainError.VideoExtractionFailed(Url(url), stderr.takeLast(2000)).left()
            }

            val json = Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(stdout).jsonObject

            VideoInfo(
                videoId = VideoId(obj.getString("id")),
                extractor = Extractor(obj.getStringOrDefault("extractor_key", "generic").lowercase()),
                title = obj.getString("title"),
                channelId = ChannelId(
                    obj.getStringOrNull("channel_id")?.takeIf { it.isNotBlank() }
                        ?: obj.getStringOrNull("uploader_id")?.takeIf { it.isNotBlank() }
                        ?: "unknown"
                ),
                channelName = obj.getStringOrNull("channel")?.takeIf { it.isNotBlank() }
                    ?: obj.getStringOrNull("uploader")?.takeIf { it.isNotBlank() }
                    ?: "Unknown",
                uploadDate = obj.getStringOrNull("upload_date")?.let { parseUploadDate(it) },
                duration = (obj.getDoubleOrNull("duration") ?: 0.0).seconds,
                webpageUrl = Url(obj.getStringOrDefault("webpage_url", url)),
                thumbnails = obj["thumbnails"]?.jsonArray?.mapNotNull { thumb ->
                    val thumbObj = thumb.jsonObject
                    val thumbUrl = thumbObj.getStringOrNull("url") ?: return@mapNotNull null
                    VideoInfo.Thumbnail(
                        url = Url(thumbUrl),
                        width = thumbObj.getIntOrNull("width"),
                        height = thumbObj.getIntOrNull("height"),
                    )
                } ?: emptyList(),
                description = obj.getStringOrNull("description"),
                viewCount = obj.getLongOrNull("view_count"),
                availableFormats = obj["formats"]?.jsonArray?.map { fmt ->
                    val fmtObj = fmt.jsonObject
                    VideoInfo.Format(
                        formatId = fmtObj.getString("format_id"),
                        extension = fmtObj.getString("ext"),
                        width = fmtObj.getIntOrNull("width"),
                        height = fmtObj.getIntOrNull("height"),
                        fps = fmtObj.getDoubleOrNull("fps"),
                        tbr = fmtObj.getDoubleOrNull("tbr"),
                        vcodec = fmtObj.getStringOrNull("vcodec"),
                        acodec = fmtObj.getStringOrNull("acodec"),
                        formatNote = fmtObj.getStringOrNull("format_note"),
                        filesize = fmtObj.getLongOrNull("filesize"),
                        filesizeApprox = fmtObj.getLongOrNull("filesize_approx"),
                    )
                } ?: emptyList(),
            ).right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract video info from $url" }
            DomainError.VideoExtractionFailed(Url(url), e.message ?: "Unknown error").left()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun download(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo?,
    ): Either<DomainError, FilePath> = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(config.path)
                add("-o"); add(outputPath.value)
                add("--retries"); add(config.retries.toString())
                add("--fragment-retries"); add(config.fragmentRetries.toString())
                add("--no-playlist")
                addCookiesArgs()
                addSslArgs(url.value)
                addFormatArgs(policy.maxQuality, videoInfo)
                addResilienceArgs()
                policy.preferredContainer?.let { add("--merge-output-format"); add(it.extension) }
                effectiveProxyUrl(url.value)?.let { add("--proxy"); add(it) }

                add(url.value)
            }

            logger.info { "yt-dlp command: ${args.joinToString(" ")}" }
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .enrichPath()
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "yt-dlp download failed (exit=$exitCode): ${output.takeLast(2000)}" }
                return@withContext DomainError.DownloadFailed(
                    JobId(kotlin.uuid.Uuid.random()),
                    output.takeLast(2000),
                ).left()
            }

            outputPath.right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to download ${url.value}" }
            DomainError.DownloadFailed(
                JobId(kotlin.uuid.Uuid.random()),
                e.message ?: "Unknown error",
            ).left()
        }
    }

    override fun downloadWithProgress(
        url: Url,
        outputPath: FilePath,
        policy: DownloadPolicy,
        videoInfo: VideoInfo?,
    ): Flow<DownloadEvent> = flow {
        val formats = videoInfo?.availableFormats
        val selectedFormatId = if (formats != null && formats.isNotEmpty()) {
            resolveBestFormatId(formats, policy.maxQuality)
        } else null

        val args = buildList {
            add(config.path)
            add("-o"); add(outputPath.value)
            add("--newline")
            add("--retries"); add(config.retries.toString())
            add("--fragment-retries"); add(config.fragmentRetries.toString())
            add("--no-playlist")
            addCookiesArgs()
            addSslArgs(url.value)
            addFormatArgs(policy.maxQuality, videoInfo)
            addResilienceArgs()
            policy.preferredContainer?.let { add("--merge-output-format"); add(it.extension) }

            if (policy.writeThumbnail) {
                add("--write-thumbnail")
            }
            effectiveProxyUrl(url.value)?.let { add("--proxy"); add(it) }
            add(url.value)
        }

        logger.info { "yt-dlp command: ${args.joinToString(" ")}" }

        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .enrichPath()
            .start()

        val outputLines = mutableListOf<String>()
        var downloadedFormatId: String? = null

        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                outputLines += line
                // Log informational lines about format selection, merging, and warnings
                if (line.contains("[info]") || line.contains("[merger]") || line.contains("[download] Destination")
                    || line.contains("Downloading format") || line.contains("[warning]") || line.contains("[error]")) {
                    logger.info { "yt-dlp: $line" }
                }

                // Try to extract downloaded format ID from log
                // Example: [info] BaW_jenozKc: Downloading 1 format(s): 303+251
                if (line.contains("Downloading 1 format(s):")) {
                    downloadedFormatId = line.substringAfter("Downloading 1 format(s):").trim()
                } else if (line.contains("Downloading format")) {
                    // Example: [download] Downloading format 22
                    downloadedFormatId = line.substringAfter("Downloading format").trim().split(" ").firstOrNull()
                }

                parseProgressLine(line)?.let { emit(DownloadEvent.Progress(it)) }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = outputLines.takeLast(50).joinToString("\n")
            logger.error { "yt-dlp download failed (exit=$exitCode):\n$output" }
            throw RuntimeException("yt-dlp download failed (exit=$exitCode): ${output.takeLast(500)}")
        } else {
            logger.info { "yt-dlp download completed successfully: ${outputPath.value}" }
            val actualFormatId = downloadedFormatId ?: selectedFormatId
            val actualFormat = if (actualFormatId != null && formats != null) {
                resolveActualFormat(actualFormatId, formats)
            } else null
            emit(DownloadEvent.Completed(actualFormat))
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveActualFormat(formatId: String, availableFormats: List<VideoInfo.Format>): VideoInfo.Format? {
        if (!formatId.contains("+")) {
            return availableFormats.find { it.formatId == formatId }
        }
        val ids = formatId.split("+")
        val videoFormat = availableFormats.find { it.formatId == ids[0] } ?: return null
        val audioFormat = availableFormats.find { it.formatId == ids.getOrNull(1) }
        
        return if (audioFormat != null) {
            videoFormat.copy(
                formatId = formatId,
                acodec = audioFormat.acodec,
                tbr = (videoFormat.tbr ?: 0.0) + (audioFormat.tbr ?: 0.0)
            )
        } else videoFormat
    }

    private fun parseProgressLine(line: String): DownloadProgress? {
        if (!line.contains("%")) return null
        val percentMatch = "([\\d.]+)%".toRegex().find(line) ?: return null
        val percent = percentMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: return null
        return DownloadProgress(
            percent = percent.coerceIn(0, 100),
            downloadedBytes = 0,
            totalBytes = null,
            speed = null,
            eta = null,
        )
    }

    private fun parseUploadDate(raw: String): LocalDate? =
        if (raw.length == 8) {
            try {
                LocalDate("${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}")
            } catch (_: Exception) {
                null
            }
        } else if (raw.length == 10 && raw[4] == '-') {
            try { LocalDate(raw) } catch (_: Exception) { null }
        } else null
}

// --- JSON helpers ---

private fun JsonObject.getString(key: String): String =
    this[key]?.jsonPrimitive?.content ?: error("Missing key: $key")

private fun JsonObject.getStringOrDefault(key: String, default: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: default

private fun JsonObject.getStringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.getDoubleOrNull(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.getIntOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.getLongOrNull(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull

