package io.github.alelk.tgvd.server.infra.process

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.*
import io.github.alelk.tgvd.domain.job.DownloadProgress
import io.github.alelk.tgvd.domain.job.VideoDownloader
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
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
     * Format selector for a given quality.
     *
     * Strategy:
     * 1. Use `bestvideo*+bestaudio/bestvideo*` as format selector — this selects
     *    the best video stream (with or without audio) merged with the best audio stream.
     *    The fallback `bestvideo*` (without separate audio) is used only when no audio
     *    stream is available (e.g. some extractors).
     * 2. IMPORTANT: We intentionally do NOT fall back to `best` (muxed format).
     *    YouTube and similar sites serve muxed progressive formats only in low quality
     *    (typically 360p/480p). When the network is slow/unstable, yt-dlp might fail
     *    to download DASH fragments and silently fall back to `best`, resulting in
     *    very poor quality. It is better to fail and retry than to silently download 360p.
     * 3. Use `-S` (--format-sort) to control maximum resolution:
     *    - `res:1080` means "prefer formats closest to 1080p but not higher"
     * 4. The `*` suffix on `bestvideo*` allows formats that contain both video and audio
     *    (e.g. AV1/VP9 adaptive formats).
     */
    private fun MutableList<String>.addFormatArgs(quality: DownloadPolicy.VideoQuality) {
        // Select best video + best audio; fallback to bestvideo* (no audio) — never to low-quality muxed 'best'
        add("-f"); add("bestvideo*+bestaudio/bestvideo*")

        when (quality) {
            DownloadPolicy.VideoQuality.BEST -> {
                // No resolution cap — just pick the highest quality available
                add("-S"); add("res,tbr,fps")
            }
            DownloadPolicy.VideoQuality.HD_1080 -> {
                // Cap at 1080p — yt-dlp will pick the closest resolution ≤1080
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
    }

    override suspend fun extract(url: String): Either<DomainError, VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(config.path)
                add("--dump-json")
                add("--no-download")
                add("--no-playlist")
                addCookiesArgs()
                proxyConfig.toUrl()?.let { add("--proxy"); add(it) }
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
                channelId = ChannelId(obj.getStringOrDefault("channel_id", obj.getStringOrDefault("uploader_id", "unknown"))),
                channelName = obj.getStringOrDefault("channel", obj.getStringOrDefault("uploader", "Unknown")),
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
    ): Either<DomainError, FilePath> = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(config.path)
                add("-o"); add(outputPath.value)
                add("--retries"); add(config.retries.toString())
                add("--fragment-retries"); add(config.fragmentRetries.toString())
                add("--no-playlist")
                addCookiesArgs()
                addFormatArgs(policy.maxQuality)
                addResilienceArgs()
                policy.preferredContainer?.let { add("--merge-output-format"); add(it.extension) }
                proxyConfig.toUrl()?.let { add("--proxy"); add(it) }

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
    ): Flow<DownloadProgress> = flow {
        val args = buildList {
            add(config.path)
            add("-o"); add(outputPath.value)
            add("--newline")
            add("--retries"); add(config.retries.toString())
            add("--fragment-retries"); add(config.fragmentRetries.toString())
            add("--no-playlist")
            addCookiesArgs()
            addFormatArgs(policy.maxQuality)
            addResilienceArgs()
            policy.preferredContainer?.let { add("--merge-output-format"); add(it.extension) }

            if (policy.writeThumbnail) {
                add("--write-thumbnail")
            }
            proxyConfig.toUrl()?.let { add("--proxy"); add(it) }
            add(url.value)
        }

        logger.info { "yt-dlp command: ${args.joinToString(" ")}" }

        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .enrichPath()
            .start()

        val outputLines = mutableListOf<String>()
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                outputLines += line
                // Log informational lines about format selection, merging, and warnings
                if (line.contains("[info]") || line.contains("[merger]") || line.contains("[download] Destination")
                    || line.contains("Downloading format") || line.contains("[warning]") || line.contains("[error]")) {
                    logger.info { "yt-dlp: $line" }
                }
                parseProgressLine(line)?.let { emit(it) }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = outputLines.takeLast(50).joinToString("\n")
            logger.error { "yt-dlp download failed (exit=$exitCode):\n$output" }
            throw RuntimeException("yt-dlp download failed (exit=$exitCode): ${output.takeLast(500)}")
        } else {
            logger.info { "yt-dlp download completed successfully: ${outputPath.value}" }
        }
    }.flowOn(Dispatchers.IO)

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

