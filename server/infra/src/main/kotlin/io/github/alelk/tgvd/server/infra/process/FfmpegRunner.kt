package io.github.alelk.tgvd.server.infra.process

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.storage.AudioFormat
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.VideoEncodeSettings
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

class FfmpegRunner(
    private val config: FfmpegConfig,
) {

    suspend fun convertVideo(
        input: FilePath,
        output: FilePath,
        container: MediaContainer,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        encodeSettings: VideoEncodeSettings? = null,
    ): Either<DomainError, FilePath> {
        val settings = encodeSettings ?: VideoEncodeSettings()

        // Determine if transcoding is actually needed:
        // If maxHeight is set, check source resolution — skip transcoding if source is already within limits
        val sourceResolution = if (maxHeight != null || maxWidth != null) probeVideoResolution(input) else null
        val sourceWidth = sourceResolution?.first
        val sourceHeight = sourceResolution?.second
        val needsTranscode = when {
            maxHeight == null && maxWidth == null -> false
            sourceHeight == null || sourceWidth == null -> true // can't determine — transcode to be safe
            maxHeight != null && sourceHeight > maxHeight -> true
            maxWidth != null && sourceWidth > maxWidth -> true
            else -> false
        }

        if ((maxHeight != null || maxWidth != null) && !needsTranscode) {
            logger.info { "Source resolution (${sourceWidth}x${sourceHeight}) within limits (${maxWidth ?: "any"}x${maxHeight ?: "any"}), will remux only" }
        }

        val args = buildList {
            // HW accel init args (must come before -i)
            if (needsTranscode) {
                settings.hwAccel?.let { hw ->
                    when (hw) {
                        VideoEncodeSettings.HwAccel.VIDEOTOOLBOX -> {} // no init flag needed
                        VideoEncodeSettings.HwAccel.NVENC -> { add("-hwaccel"); add("cuda") }
                        VideoEncodeSettings.HwAccel.QSV -> {
                            // Intel QSV via VAAPI → QSV chain (mirrors Jellyfin approach):
                            // 1. init_hw_device vaapi=va — VAAPI device (auto-detect driver)
                            // 2. init_hw_device qsv=qs@va — QSV device derived from VAAPI
                            // 3. filter_hw_device qs — use QSV device for vf filters
                            // 4. hwaccel vaapi — decode via VAAPI
                            // 5. hwaccel_output_format vaapi — decoded frames stay on VAAPI surface
                            // 6. extra_hw_frames — reserve extra surfaces for filter pipeline
                            add("-init_hw_device"); add("vaapi=va:")
                            add("-init_hw_device"); add("qsv=qs@va")
                            add("-filter_hw_device"); add("qs")
                            add("-hwaccel"); add("vaapi")
                            add("-hwaccel_output_format"); add("vaapi")
                            add("-extra_hw_frames"); add("64")
                        }
                        VideoEncodeSettings.HwAccel.VAAPI -> { add("-hwaccel"); add("vaapi"); add("-hwaccel_output_format"); add("vaapi") }
                        VideoEncodeSettings.HwAccel.AMF -> {} // no init flag needed
                    }
                }
            }

            add("-i"); add(input.value)

            if (needsTranscode) {
                // Video scaling — fit within maxWidth x maxHeight box, preserving aspect ratio.
                // For QSV: use scale_vaapi + hwmap (vaapi surface → qsv surface) as Jellyfin does.
                // For others: standard scale filter with software or hwaccel-specific approach.
                val scaleFilter = buildScaleFilter(maxWidth, maxHeight, settings.hwAccel)
                add("-vf"); add(scaleFilter)
                // Video encoder
                add("-c:v"); add(settings.resolveEncoder())
                // Quality
                val isHw = settings.hwAccel != null
                if (isHw) {
                    when (settings.hwAccel) {
                        VideoEncodeSettings.HwAccel.VIDEOTOOLBOX -> {
                            // VideoToolbox uses -q:v with INVERTED scale: 1 = worst, 100 = best (lossless)
                            // CRF uses: 0 = lossless, 51 = worst
                            // Convert: q:v = round((51 - crf) / 51 * 99) + 1
                            val vtQuality = ((51 - settings.crf).toDouble() / 51.0 * 99.0 + 1.0).toInt().coerceIn(1, 100)
                            add("-q:v"); add("$vtQuality")
                        }
                        VideoEncodeSettings.HwAccel.NVENC -> {
                            // NVENC -cq uses same 0-51 scale as CRF (0 = lossless, 51 = worst)
                            add("-cq"); add("${settings.crf}")
                            // Map user preset to NVENC p1-p7 scale
                            add("-preset"); add(nvencPreset(settings.preset))
                        }
                        VideoEncodeSettings.HwAccel.QSV -> {
                            add("-low_power"); add("1")
                            add("-preset"); add("medium")
                            add("-global_quality"); add("${settings.crf}")
                        }
                        else -> { add("-crf"); add("${settings.crf}") }
                    }
                } else {
                    add("-crf"); add("${settings.crf}")
                    add("-preset"); add(settings.preset.ffmpegValue)
                }
                // Audio
                add("-c:a"); add(settings.resolveAudioCodec(container))
                add("-b:a"); add(settings.audioBitrate)
            } else {
                // No transcoding — just remux
                add("-c:v"); add("copy")
                add("-c:a"); add("copy")
            }
            add("-y"); add(output.value)
        }

        val desc = if (needsTranscode) {
            val encoder = settings.resolveEncoder()
            val sizeDesc = listOfNotNull(maxWidth?.let { "${it}w" }, maxHeight?.let { "${it}p" }).joinToString("x")
            "convert to ${container.extension} ($sizeDesc, $encoder, crf=${settings.crf})"
        } else {
            val limits = listOfNotNull(maxWidth?.let { "${it}w" }, maxHeight?.let { "${it}p" }).joinToString("x")
            "remux to ${container.extension}" + if (limits.isNotEmpty()) " (source ${sourceWidth}x${sourceHeight} within $limits)" else ""
        }

        val result = runFfmpeg(args = args, description = desc)

        // If hardware encoder failed — retry with software fallback
        if (result.isLeft() && settings.hwAccel != null) {
            val fallbackSettings = settings.copy(hwAccel = null)
            logger.warn { "Hardware encoder (${settings.hwAccel}) failed, retrying with software encoder (${fallbackSettings.resolveEncoder()})" }
            val fallbackArgs = buildList {
                add("-i"); add(input.value)
                if (needsTranscode) {
                    val scaleFilter = buildScaleFilter(maxWidth, maxHeight)
                    add("-vf"); add(scaleFilter)
                    add("-c:v"); add(fallbackSettings.resolveEncoder())
                    add("-crf"); add("${fallbackSettings.crf}")
                    add("-preset"); add(fallbackSettings.preset.ffmpegValue)
                    add("-c:a"); add(fallbackSettings.resolveAudioCodec(container))
                    add("-b:a"); add(fallbackSettings.audioBitrate)
                } else {
                    add("-c:v"); add("copy")
                    add("-c:a"); add("copy")
                }
                add("-y"); add(output.value)
            }
            val fallbackDesc = if (needsTranscode) {
                val sizeDesc = listOfNotNull(maxWidth?.let { "${it}w" }, maxHeight?.let { "${it}p" }).joinToString("x")
                "convert to ${container.extension} ($sizeDesc, ${fallbackSettings.resolveEncoder()}, crf=${fallbackSettings.crf}) [sw fallback]"
            } else desc
            return runFfmpeg(args = fallbackArgs, description = fallbackDesc).map { output }
        }

        @Suppress("UNCHECKED_CAST")
        return result.map { output } as Either<DomainError, FilePath>
    }

    /**
     * Build ffmpeg video filter for scaling.
     *
     * - QSV: scale via scale_vaapi on VAAPI surface, then hwmap to QSV for h264_qsv encoder.
     *   Pipeline: scale_vaapi=w=W:h=H:format=nv12,hwmap=derive_device=qsv,format=qsv
     * - VAAPI: scale_vaapi filter directly.
     * - Others (SW, NVENC, VideoToolbox, AMF): standard software scale filter.
     *
     * HW filters (scale_vaapi) preserve aspect ratio when one dimension is -1.
     * SW filter: fits within box, never upscales, rounds to even.
     */
    private fun buildScaleFilter(
        maxWidth: Int?,
        maxHeight: Int?,
        hwAccel: VideoEncodeSettings.HwAccel? = null,
    ): String {
        return when (hwAccel) {
            VideoEncodeSettings.HwAccel.QSV -> {
                val w = maxWidth ?: -1
                val h = maxHeight ?: -1
                if (maxWidth != null || maxHeight != null) {
                    // scale_vaapi scales on VAAPI surface, hwmap maps result to QSV for h264_qsv encoder
                    "scale_vaapi=w=$w:h=$h:format=nv12," +
                        "hwmap=derive_device=qsv,format=qsv"
                } else {
                    // No resize — just map VAAPI surface to QSV
                    "hwmap=derive_device=qsv,format=qsv"
                }
            }
            VideoEncodeSettings.HwAccel.VAAPI -> {
                val w = maxWidth ?: -1
                val h = maxHeight ?: -1
                if (maxWidth != null || maxHeight != null) {
                    "scale_vaapi=w=$w:h=$h:format=nv12"
                } else {
                    "scale_vaapi=format=nv12"
                }
            }
            else -> {
                // Standard software scale filter — fit within box, never upscale, round to even
                when {
                    maxWidth != null && maxHeight != null ->
                        "scale='trunc(iw*min(1\\,min($maxWidth/iw\\,$maxHeight/ih))/2)*2':'trunc(ih*min(1\\,min($maxWidth/iw\\,$maxHeight/ih))/2)*2'"
                    maxHeight != null ->
                        "scale=-2:'min($maxHeight\\,ih)'"
                    maxWidth != null ->
                        "scale='min($maxWidth\\,iw)':-2"
                    else -> error("No dimension constraint specified")
                }
            }
        }
    }

    /**
     * Map generic EncodePreset to NVENC preset names (p1=fastest .. p7=slowest).
     */
    private fun nvencPreset(preset: VideoEncodeSettings.EncodePreset): String = when (preset) {
        VideoEncodeSettings.EncodePreset.ULTRAFAST -> "p1"
        VideoEncodeSettings.EncodePreset.SUPERFAST -> "p1"
        VideoEncodeSettings.EncodePreset.VERYFAST -> "p2"
        VideoEncodeSettings.EncodePreset.FASTER -> "p3"
        VideoEncodeSettings.EncodePreset.FAST -> "p4"
        VideoEncodeSettings.EncodePreset.MEDIUM -> "p4"
        VideoEncodeSettings.EncodePreset.SLOW -> "p5"
        VideoEncodeSettings.EncodePreset.SLOWER -> "p6"
        VideoEncodeSettings.EncodePreset.VERYSLOW -> "p7"
    }

    /**
     * Probe video width and height using ffprobe. Returns (width, height) pair or null if detection fails.
     */
    private suspend fun probeVideoResolution(input: FilePath): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val ffprobePath = config.path.replace("ffmpeg", "ffprobe")
            val process = ProcessBuilder(
                ffprobePath, "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=p=0",
                input.value,
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Output format: "width,height" e.g. "2579,1080"
                val parts = output.lines().firstOrNull()?.trim()?.split(",")
                val width = parts?.getOrNull(0)?.trim()?.toIntOrNull()
                val height = parts?.getOrNull(1)?.trim()?.toIntOrNull()
                if (width != null && height != null) {
                    logger.info { "Probed video resolution: ${width}x${height} for ${input.fileName}" }
                    width to height
                } else {
                    logger.warn { "Could not parse ffprobe resolution output: $output" }
                    null
                }
            } else {
                logger.warn { "ffprobe failed (exit=$exitCode): $output" }
                null
            }
        } catch (e: Exception) {
            logger.warn { "ffprobe not available: ${e.message}" }
            null
        }
    }

    suspend fun extractAudio(
        input: FilePath,
        output: FilePath,
        format: AudioFormat,
    ): Either<DomainError, FilePath> = runFfmpeg(
        args = listOf("-i", input.value, "-vn", "-c:a", audioCodecFor(format), "-y", output.value),
        description = "extract audio as ${format.extension}",
    ).map { output }

    suspend fun embedMetadata(
        input: FilePath,
        output: FilePath,
        metadata: Map<String, String>,
    ): Either<DomainError, FilePath> {
        val metadataArgs = metadata.flatMap { (key, value) -> listOf("-metadata", "$key=$value") }
        return runFfmpeg(
            args = listOf("-i", input.value) + metadataArgs + listOf("-c", "copy", "-y", output.value),
            description = "embed metadata",
        ).map { output }
    }

    suspend fun embedThumbnail(
        input: FilePath,
        thumbnail: FilePath,
        output: FilePath,
    ): Either<DomainError, FilePath> {
        val thumbExt = thumbnail.extension.lowercase()
        val outputExt = output.extension.lowercase()
        val isMp4 = outputExt == "mp4" || outputExt == "m4a" || outputExt == "m4v"

        // For MP4: webp thumbnails must be converted; use mjpeg codec for the cover art stream
        val args = buildList {
            add("-i"); add(input.value)
            add("-i"); add(thumbnail.value)
            add("-map"); add("0")
            add("-map"); add("1")
            add("-c"); add("copy")
            if (isMp4) {
                // MP4 doesn't support webp covers; transcode thumbnail to mjpeg
                if (thumbExt == "webp" || thumbExt == "png") {
                    add("-c:v:1"); add("mjpeg")
                    add("-q:v:1"); add("2")  // high quality jpeg
                } else {
                    add("-c:v:1"); add("copy")
                }
                add("-disposition:v:1"); add("attached_pic")
            } else {
                add("-disposition:v:1"); add("attached_pic")
            }
            add("-y"); add(output.value)
        }

        return runFfmpeg(args = args, description = "embed thumbnail").map { output }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runFfmpeg(
        args: List<String>,
        description: String,
    ): Either<DomainError, Unit> = withContext(Dispatchers.IO) {
        try {
            val command = listOf(config.path) + args
            logger.info { "Running ffmpeg ($description): ${command.joinToString(" ")}" }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error { "ffmpeg failed (exit=$exitCode, $description): ${output.takeLast(500)}" }
                DomainError.PostProcessingFailed(
                    jobId = JobId(kotlin.uuid.Uuid.random()),
                    phase = JobPhase.CONVERT,
                    cause = output.takeLast(500),
                ).left()
            } else {
                Unit.right()
            }
        } catch (e: Exception) {
            logger.error(e) { "ffmpeg execution failed ($description)" }
            DomainError.PostProcessingFailed(
                jobId = JobId(kotlin.uuid.Uuid.random()),
                phase = JobPhase.CONVERT,
                cause = e.message ?: "Unknown error",
            ).left()
        }
    }

    private fun audioCodecFor(format: AudioFormat): String = when (format) {
        AudioFormat.M4A -> "aac"
        AudioFormat.MP3 -> "libmp3lame"
        AudioFormat.OPUS -> "libopus"
        AudioFormat.FLAC -> "flac"
        AudioFormat.WAV -> "pcm_s16le"
    }
}

