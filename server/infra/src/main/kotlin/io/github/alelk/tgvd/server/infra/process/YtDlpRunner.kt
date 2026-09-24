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
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpExtractorOverride
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private fun List<String>.safeCommand(): String = mapIndexed { index, argument ->
        if (index > 0 && this[index - 1] == "--proxy") "<redacted>" else argument
    }.joinToString(" ")

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

    /**
     * Managed cookies file: when [YtDlpConfig.cookiesContent] is set, the text is written here
     * so yt-dlp can pick it up via --cookies.
     */
    private val managedCookiesFile: java.io.File
        get() = java.io.File(System.getProperty("java.io.tmpdir"), "tgvd-managed-cookies.txt")

    /** Append cookies arguments from config (--cookies-from-browser or --cookies). Priority: browser > content > file. */
    private fun MutableList<String>.addCookiesArgs() {
        config.cookiesFromBrowser?.takeIf { it.isNotBlank() }?.let {
            add("--cookies-from-browser"); add(it)
            return
        }
        config.cookiesContent?.takeIf { it.isNotBlank() }?.let { content ->
            try {
                managedCookiesFile.writeText(content)
                add("--cookies"); add(managedCookiesFile.absolutePath)
            } catch (e: Exception) {
                logger.error(e) { "Failed to write managed cookies file" }
            }
            return
        }
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
     * 1. If [YtDlpConfig.preferredFormats] is set — use it directly as `-f` (global override, skips auto-selection).
     * 2. If [videoInfo] is provided, try to pre-select best video and audio format IDs
     *    manually from [VideoInfo.availableFormats] to ensure the highest quality is used.
     * 3. Otherwise, use `bestvideo*+bestaudio/bestvideo*` as format selector.
     * 4. `-S` (--format-sort): use [YtDlpConfig.formatSort] if set, otherwise derive from quality.
     */
    private fun MutableList<String>.addFormatArgs(
        policy: DownloadPolicy,
        videoInfo: VideoInfo? = null,
        mediaSelection: MediaSelection? = null,
    ) {
        val quality = policy.maxQuality
        // Global override from settings takes highest priority
        val preferredFormats = config.preferredFormats
        if (!preferredFormats.isNullOrBlank() && mediaSelection?.audioFormatIds == null) {
            add("-f"); add(preferredFormats)
            val sortStr = config.formatSort?.takeIf { it.isNotBlank() } ?: qualitySortString(quality)
            add("-S"); add(sortStr)
            return
        }

        val formats = videoInfo?.availableFormats
        if (formats != null && formats.isNotEmpty()) {
            val selection = selectFormats(formats, policy, mediaSelection)
            val bestFormatId = selection.formatSelector
            if (bestFormatId != null) {
                logger.info {
                    "Selected formats: video=${selection.video?.formatId ?: selection.combined?.formatId}, " +
                        "originalAudio=${selection.originalAudio?.formatId}:${selection.originalAudio?.language ?: "unknown"}, " +
                        "additionalAudio=${selection.additionalAudio.joinToString { "${it.formatId}:${it.language ?: "unknown"}" }}"
                }
                add("-f"); add(bestFormatId)
                if (selection.audioTracks.size > 1) {
                    add("--audio-multistreams")
                    // The original audio is deliberately the first selected audio stream.
                    add("--postprocessor-args")
                    add("Merger+ffmpeg_o:-disposition:a 0 -disposition:a:0 default")
                }
                val sortStr = config.formatSort?.takeIf { it.isNotBlank() } ?: qualitySortString(quality)
                add("-S"); add(sortStr)
                return
            }
        }

        // Fallback to general strategy
        add("-f"); add("bestvideo*+bestaudio/bestvideo*")
        if (config.checkFormats) add("--check-formats")
        val sortStr = config.formatSort?.takeIf { it.isNotBlank() } ?: qualitySortString(quality)
        add("-S"); add(sortStr)
    }

    private fun qualitySortString(quality: DownloadPolicy.VideoQuality): String = when (quality) {
        DownloadPolicy.VideoQuality.BEST    -> "res,tbr,fps"
        DownloadPolicy.VideoQuality.HD_1080 -> "res:1080,tbr,fps"
        DownloadPolicy.VideoQuality.HD_720  -> "res:720,tbr,fps"
        DownloadPolicy.VideoQuality.SD_480  -> "res:480,tbr,fps"
    }

    internal fun resolveBestFormatId(
        formats: List<VideoInfo.Format>,
        quality: DownloadPolicy.VideoQuality,
    ): String? = AudioTrackSelector.select(formats, quality, emptyList(), 0).formatSelector

    internal fun selectFormats(
        formats: List<VideoInfo.Format>, policy: DownloadPolicy,
        mediaSelection: MediaSelection? = null,
    ): AudioTrackSelector.Selection {
        val automatic = AudioTrackSelector.select(formats, policy, config)
        val selectedIds = mediaSelection?.audioFormatIds ?: return automatic
        val tracks = selectedIds.mapNotNull { id -> formats.find { it.formatId == id } }
        return automatic.copy(originalAudio = tracks.firstOrNull(), additionalAudio = tracks.drop(1))
    }

    // The user's chosen Output format (rendered into the literal output path extension) is the
    // sole source of truth for the merge container — never silently substitute a different one.
    internal fun effectiveContainer(outputPath: FilePath): String? =
        outputPath.extension.takeIf { it.isNotBlank() }

    /** Append retry/resilience arguments for robust downloads on slow/unstable networks. */
    private fun MutableList<String>.addResilienceArgs() {
        // Retry individual fragment downloads more aggressively
        add("--extractor-retries"); add("5")
        // Sleep between fragment retries to avoid rate limiting and let transient issues resolve
        add("--retry-sleep"); add("fragment:exp=1:5:30")
        // Sleep between file-level retries
        add("--retry-sleep"); add("http:exp=1:2:30")
        // Socket timeout — use config value (default 30s)
        add("--socket-timeout"); add(config.socketTimeout.toString())
        // Concurrent fragment downloads — speeds up DASH/HLS downloads significantly
        add("--concurrent-fragments"); add(config.concurrentFragments.toString())
    }

    /** Append network/anti-ban arguments from config (rate limit, sleep, user-agent). */
    private fun MutableList<String>.addNetworkArgs() {
        config.rateLimit?.takeIf { it.isNotBlank() }?.let { add("--rate-limit"); add(it) }
        config.sleepInterval?.let { sleep ->
            add("--sleep-interval"); add(sleep.toString())
            config.maxSleepInterval?.let { max -> add("--max-sleep-interval"); add(max.toString()) }
        }
        config.userAgent?.takeIf { it.isNotBlank() }?.let { add("--user-agent"); add(it) }
    }

    /** Append the effective global/per-rule/per-channel subtitle arguments. */
    private fun MutableList<String>.addSubtitleArgs(policy: DownloadPolicy, mediaSelection: MediaSelection? = null) {
        val subtitles = SubtitleSelector.select(config, policy, mediaSelection?.subtitleLanguages)
        logger.info {
            "yt-dlp subtitles: global=${config.writeSubs}/${config.writeAutoSubs}, " +
                "rule/channel=${policy.downloadSubtitles ?: "inherit"}, selected=${mediaSelection?.subtitleLanguages}, " +
                "effective=${subtitles.enabled}, languages=${subtitles.languages}"
        }
        addAll(subtitles.arguments())
    }

    /**
     * Builds the effective `--extractor-args` value by merging [youtubePlayerClient] and [extractorArgs].
     *
     * Rules:
     * - If [extractorArgs] already contains "player_client" → use it as-is (explicit user override).
     * - If [youtubePlayerClient] is set → prepend `youtube:player_client=<value>` to [extractorArgs].
     * - Otherwise → use [extractorArgs] alone (may be null).
     */
    private fun effectiveExtractorArgs(): String? {
        val userArgs = config.extractorArgs?.takeIf { it.isNotBlank() }
        val client = config.youtubePlayerClient.takeIf { it.isNotBlank() }
        return when {
            userArgs != null && userArgs.contains("player_client") -> userArgs   // user set it explicitly
            client != null && userArgs != null -> "youtube:player_client=$client;$userArgs"
            client != null -> "youtube:player_client=$client"
            else -> userArgs
        }
    }

    /** Append site-specific arguments (extractor-args, sponsorblock). */
    private fun MutableList<String>.addSiteArgs() {
        effectiveExtractorArgs()?.let { add("--extractor-args"); add(it) }
        config.sponsorBlockRemove?.takeIf { it.isNotBlank() }?.let { add("--sponsorblock-remove"); add(it) }
    }

    /**
     * Run yt-dlp `--dump-json` for [url] with the provided args and return (exitCode, stdout, stderr).
     */
    private suspend fun runExtractProcess(args: List<String>): Triple<Int, String, String> = coroutineScope {
        val process = ProcessBuilder(args)
            .redirectErrorStream(false)
            .enrichPath()
            .start()
        val stdoutDeferred = async { process.inputStream.bufferedReader().use { it.readText() } }
        val stderrDeferred = async { process.errorStream.bufferedReader().use { it.readText() } }
        val stdout = stdoutDeferred.await()
        val stderr = stderrDeferred.await()
        Triple(process.waitFor(), stdout, stderr)
    }

    private fun buildExtractArgs(url: String): List<String> = buildList {
        add(config.path)
        add("--dump-json")
        add("--no-download")
        add("--no-playlist")
        addCookiesArgs()
        addSslArgs(url)
        addSiteArgs()
        addNetworkArgs()
        add("--socket-timeout"); add(config.socketTimeout.toString())
        effectiveProxyUrl(url)?.let { add("--proxy"); add(it) }
        add(url)
    }

    override suspend fun extract(url: String): Either<DomainError, VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val args = buildExtractArgs(url)
            val effectiveArgs = effectiveExtractorArgs()
            logger.info {
                "Extracting video info: yt-dlp --dump-json $url " +
                    "(player_client=${config.youtubePlayerClient.ifBlank { "yt-dlp default" }}, " +
                    "extractor-args=${effectiveArgs ?: "none"}, " +
                    "proxy=${if (effectiveProxyUrl(url) == null) "none" else "configured"})"
            }
            logger.debug { "yt-dlp extract full command: ${args.safeCommand()}" }

            val (exitCode, stdout, stderr) = runExtractProcess(args)

            if (exitCode != 0) {
                logger.error { "yt-dlp extract failed (exit=$exitCode):\nSTDERR: $stderr\nSTDOUT (last 500): ${stdout.takeLast(500)}" }
                logger.debug { "yt-dlp extract command was: ${args.safeCommand()}" }
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
                subtitleTracks = listOf("subtitles" to false, "automatic_captions" to true)
                    .flatMap { (key, automatic) ->
                        obj[key]?.jsonObject?.flatMap { (language, tracks) ->
                            tracks.jsonArray.map { track ->
                                VideoInfo.SubtitleTrack(
                                    language = language,
                                    automatic = automatic,
                                    name = track.jsonObject.getStringOrNull("name"),
                                )
                            }.distinctBy { it.language to it.automatic }
                        } ?: emptyList()
                    },
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
                        language = fmtObj.getStringOrNull("language"),
                        languagePreference = fmtObj.getIntOrNull("language_preference"),
                        audioChannels = fmtObj.getIntOrNull("audio_channels"),
                        audioTrackName = fmtObj.getStringOrNull("format_note"),
                        isOriginalAudio = fmtObj.getBooleanOrNull("is_original") == true ||
                            fmtObj.getStringOrNull("format_note")?.let {
                                it.contains("original", ignoreCase = true) ||
                                    it.contains("default", ignoreCase = true)
                            } == true || (fmtObj.getIntOrNull("language_preference") ?: 0) > 0,
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
        mediaSelection: MediaSelection?,
    ): Either<DomainError, FilePath> = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(config.path)
                add("-o"); add(outputPath.value)
                if (mediaSelection?.audioFormatIds != null) add("--force-overwrites")
                add("--retries"); add(config.retries.toString())
                add("--fragment-retries"); add(config.fragmentRetries.toString())
                add("--no-playlist")
                addCookiesArgs()
                addSslArgs(url.value)
                addFormatArgs(policy, videoInfo, mediaSelection)
                addResilienceArgs()
                addNetworkArgs()
                addSubtitleArgs(policy, mediaSelection)
                addSiteArgs()
                val container = effectiveContainer(outputPath)
                container?.takeIf { it.isNotBlank() }?.let { add("--merge-output-format"); add(it) }
                effectiveProxyUrl(url.value)?.let { add("--proxy"); add(it) }

                add(url.value)
            }

            logger.info { "yt-dlp command: ${args.safeCommand()}" }
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
        mediaSelection: MediaSelection?,
    ): Flow<DownloadEvent> = flow {
        val formats = videoInfo?.availableFormats
        val selectedFormatId = if (formats != null && formats.isNotEmpty()) {
            selectFormats(formats, policy, mediaSelection).formatSelector
        } else null

        val args = buildList {
            add(config.path)
            add("-o"); add(outputPath.value)
            if (mediaSelection?.audioFormatIds != null) add("--force-overwrites")
            add("--newline")
            add("--retries"); add(config.retries.toString())
            add("--fragment-retries"); add(config.fragmentRetries.toString())
            add("--no-playlist")
            addCookiesArgs()
            addSslArgs(url.value)
            addFormatArgs(policy, videoInfo, mediaSelection)
            addResilienceArgs()
            addNetworkArgs()
            addSubtitleArgs(policy, mediaSelection)
            addSiteArgs()
            val container = effectiveContainer(outputPath)
            container?.takeIf { it.isNotBlank() }?.let { add("--merge-output-format"); add(it) }

            if (policy.writeThumbnail) {
                add("--write-thumbnail")
            }
            effectiveProxyUrl(url.value)?.let { add("--proxy"); add(it) }
            add(url.value)
        }

        var downloadedFormatId: String? = null
        logger.info { "yt-dlp command: ${args.safeCommand()}" }
        val process = ProcessBuilder(args).redirectErrorStream(true).enrichPath().start()
        val outputLines = mutableListOf<String>()
        val progressTracker = MediaProgressTracker(selectedFormatId?.split('+')?.size ?: 1)
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

                progressTracker.onLine(line)?.let { emit(DownloadEvent.Progress(it)) }
            }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = outputLines.takeLast(150).joinToString("\n")
            val phase = if (output.contains("Postprocessing") || output.contains("[Merger]")) "postprocessing/merge" else "download"
            logger.error { "yt-dlp $phase failed (exit=$exitCode):\n$output" }
            throw RuntimeException("yt-dlp $phase failed (exit=$exitCode): ${output.takeLast(4000)}")
        }
        logger.info { "yt-dlp download completed successfully: ${outputPath.value}" }
        val actualFormatId = downloadedFormatId ?: selectedFormatId
        val actualFormat = if (actualFormatId != null && formats != null) {
            resolveActualFormat(actualFormatId, formats)
        } else null
        emit(DownloadEvent.Completed(actualFormat))
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

/** yt-dlp reports 0..100 separately for each selected media stream. */
internal class MediaProgressTracker(private val streamCount: Int) {
    private var completedStreams = 0
    private var currentPercent = 0
    private var sidecar = false
    private var reportedPercent = 0

    fun onLine(line: String): DownloadProgress? {
        if (line.startsWith("[download] Destination:")) {
            val destination = line.substringAfter("Destination:").trim()
            sidecar = destination.substringAfterLast('.').lowercase() in setOf(
                "vtt", "srt", "ass", "lrc", "ttml", "json3", "webp", "jpg", "jpeg", "png",
            )
            currentPercent = 0
            return null
        }
        if (!line.startsWith("[download] ") || sidecar) return null
        val percent = Regex("^\\[download]\\s+([\\d.]+)%").find(line)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100) ?: return null
        if (percent == 100 && currentPercent < 100) completedStreams++
        currentPercent = percent
        val overall = (((completedStreams - if (percent == 100) 1 else 0) + percent / 100.0) /
            streamCount.coerceAtLeast(1) * 95).toInt().coerceIn(reportedPercent, 95)
        reportedPercent = overall
        return DownloadProgress(overall, 0, null, null, null)
    }
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

private fun JsonObject.getBooleanOrNull(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull
