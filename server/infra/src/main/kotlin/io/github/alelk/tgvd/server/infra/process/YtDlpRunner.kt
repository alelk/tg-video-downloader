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
    private val config: YtDlpConfig,
    private val proxyConfig: ProxyConfig = ProxyConfig(),
) : VideoInfoExtractor, VideoDownloader {

    override suspend fun extract(url: String): Either<DomainError, VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(config.path)
                add("--dump-json")
                add("--no-download")
                add("--no-playlist")
                proxyConfig.toUrl()?.let { add("--proxy"); add(it) }
                add(url)
            }

            logger.info { "Extracting video info: yt-dlp --dump-json $url" }
            val process = ProcessBuilder(args)
                .redirectErrorStream(false)
                .start()

            val stdoutDeferred = async { process.inputStream.bufferedReader().use { it.readText() } }
            val stderrDeferred = async { process.errorStream.bufferedReader().use { it.readText() } }
            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "yt-dlp extract failed (exit=$exitCode): $stderr" }
                return@withContext DomainError.VideoExtractionFailed(Url(url), stderr.take(500)).left()
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

                when (policy.maxQuality) {
                    DownloadPolicy.VideoQuality.BEST -> { add("-f"); add("bestvideo+bestaudio/best") }
                    DownloadPolicy.VideoQuality.HD_1080 -> { add("-f"); add("bestvideo[height<=1080]+bestaudio/best[height<=1080]") }
                    DownloadPolicy.VideoQuality.HD_720 -> { add("-f"); add("bestvideo[height<=720]+bestaudio/best[height<=720]") }
                    DownloadPolicy.VideoQuality.SD_480 -> { add("-f"); add("bestvideo[height<=480]+bestaudio/best[height<=480]") }
                }

                policy.preferredContainer?.let { add("--merge-output-format"); add(it.extension) }
                proxyConfig.toUrl()?.let { add("--proxy"); add(it) }

                add(url.value)
            }

            logger.info { "Downloading: yt-dlp -o ${outputPath.value} ${url.value}" }
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "yt-dlp download failed (exit=$exitCode): ${output.takeLast(500)}" }
                return@withContext DomainError.DownloadFailed(
                    JobId(kotlin.uuid.Uuid.random()),
                    output.takeLast(500),
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
            add("--no-playlist")
            proxyConfig.toUrl()?.let { add("--proxy"); add(it) }
            add(url.value)
        }

        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                parseProgressLine(line)?.let { emit(it) }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            logger.error { "yt-dlp download failed (exit=$exitCode)" }
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

